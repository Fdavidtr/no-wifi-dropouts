package io.multinet.mobility.data

import io.multinet.mobility.data.preferences.UserPreferencesRepository
import io.multinet.mobility.di.ApplicationScope
import io.multinet.mobility.domain.CellularWarmupState
import io.multinet.mobility.domain.ContinuityDecision
import io.multinet.mobility.domain.ContinuityPolicyEngine
import io.multinet.mobility.domain.ContinuityRuntimeState
import io.multinet.mobility.domain.ContinuitySettingsState
import io.multinet.mobility.domain.ConnectivitySnapshot
import io.multinet.mobility.domain.WifiSignalBucket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ContinuityController @Inject constructor(
    private val capabilityProbe: CapabilityProbe,
    private val connectivityRepository: ConnectivityRepository,
    private val cellularWarmupRepository: CellularWarmupRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val eventLogRepository: EventLogRepository,
    private val signalHistoryRepository: SignalHistoryRepository,
    private val policyEngine: ContinuityPolicyEngine,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var monitorJob: Job? = null
    private var lastDecisionLogKey: String? = null
    private var lastWarmupState: CellularWarmupState? = null
    private var signalSampleState = SignalSampleState()

    private val _runtimeState = MutableStateFlow(
        ContinuityRuntimeState(
            capabilities = capabilityProbe.probe(),
        ),
    )
    val runtimeState: StateFlow<ContinuityRuntimeState> = _runtimeState.asStateFlow()

    suspend fun refreshCapabilities() {
        _runtimeState.update { current ->
            current.copy(capabilities = capabilityProbe.probe())
        }
    }

    suspend fun startMonitoring() {
        mutex.withLock {
            if (monitorJob != null) return

            refreshCapabilities()
            connectivityRepository.startMonitoring()
            lastWarmupState = cellularWarmupRepository.state.value

            monitorJob = applicationScope.launch {
                combine(
                    connectivityRepository.snapshot,
                    userPreferencesRepository.settingsFlow,
                    cellularWarmupRepository.state,
                ) { snapshot, settings, warmupState ->
                    Triple(snapshot, settings, warmupState)
                }.collect { (snapshot, settings, warmupState) ->
                    recordSignalSampleIfNeeded(snapshot)

                    if (!settings.modeEnabled && warmupState != CellularWarmupState.IDLE) {
                        cellularWarmupRepository.release()
                    }

                    val decision = if (settings.modeEnabled) {
                        policyEngine.evaluate(
                            snapshot = snapshot,
                            cellularWarmupState = warmupState,
                            policy = settings.cellularPolicy,
                        )
                    } else {
                        ContinuityDecision.Idle
                    }

                    if (settings.modeEnabled) {
                        applyDecision(snapshot, decision)
                    }

                    logNetworkTransitionIfNeeded(snapshot)
                    logWarmupTransitionIfNeeded(warmupState)
                    publishRuntimeState(
                        settings = settings,
                        snapshot = snapshot,
                        warmupState = warmupState,
                        decision = decision,
                    )
                }
            }

            eventLogRepository.log(
                category = "continuity_controller",
                message = "Continuity monitoring started.",
            )
        }
    }

    suspend fun stopMonitoring() = mutex.withLock {
        monitorJob?.cancel()
        monitorJob = null
        cellularWarmupRepository.release()
        connectivityRepository.stopMonitoring()
        lastDecisionLogKey = null
        lastWarmupState = CellularWarmupState.IDLE
        signalSampleState = SignalSampleState()
        _runtimeState.update { current ->
            current.copy(
                modeEnabled = false,
                isMonitoring = false,
                cellularAvailable = false,
                cellularWarmupState = CellularWarmupState.IDLE,
                lastDecision = ContinuityDecision.Idle,
                lastTransitionAtEpochMillis = System.currentTimeMillis(),
            )
        }
        eventLogRepository.log(
            category = "continuity_controller",
            message = "Continuity monitoring stopped.",
        )
    }

    private suspend fun recordSignalSampleIfNeeded(snapshot: ConnectivitySnapshot) {
        val decision = ContinuityDiagnosticsLogic.evaluateSignalSample(
            snapshot = snapshot,
            previousState = signalSampleState,
        )
        signalSampleState = decision.updatedState

        if (!decision.shouldRecord) return

        signalHistoryRepository.record(
            snapshot = snapshot,
            thresholdRssi = checkNotNull(decision.thresholdRssi),
            bucket = decision.bucket,
        )
    }

    private suspend fun logNetworkTransitionIfNeeded(snapshot: ConnectivitySnapshot) {
        val logEvent = ContinuityDiagnosticsLogic.buildNetworkTransitionLogEvent(
            previous = _runtimeState.value.snapshot,
            current = snapshot,
        ) ?: return

        eventLogRepository.log(
            category = "network_transition",
            message = logEvent.message,
            severity = logEvent.severity,
            ssid = logEvent.ssid,
        )
    }

    private suspend fun applyDecision(
        snapshot: ConnectivitySnapshot,
        decision: ContinuityDecision,
    ) {
        when (decision) {
            is ContinuityDecision.StartCellularWarmup -> {
                cellularWarmupRepository.ensureWarmupRequested()
                logDecisionOnce(
                    key = "start:${decision.reason}",
                    category = "cellular_warmup",
                    message = decision.reason,
                    severity = "WARN",
                )
            }

            is ContinuityDecision.HoldCellularWarmup -> {
                cellularWarmupRepository.holdWarmup()
            }

            is ContinuityDecision.ReleaseCellularWarmup -> {
                cellularWarmupRepository.release()
                logDecisionOnce(
                    key = "release:${decision.reason}",
                    category = "cellular_release",
                    message = decision.reason,
                )
            }

            is ContinuityDecision.ReportBadWifi -> {
                cellularWarmupRepository.holdWarmup()
                connectivityRepository.reportBadConnectivityOnDefaultNetwork()
                    .onSuccess { reported ->
                        if (reported) {
                            policyEngine.markBadWifiReported(snapshot.currentNetworkId)
                            logDecisionOnce(
                                key = "report:${snapshot.currentNetworkId}:${decision.reason}",
                                category = "bad_wifi_report",
                                message = decision.reason,
                                severity = "WARN",
                                ssid = snapshot.wifiSsid,
                            )
                        }
                    }
                    .onFailure { throwable ->
                        logDecisionOnce(
                            key = "report_error:${throwable.message}",
                            category = "bad_wifi_report",
                            message = throwable.message ?: "Failed to report bad Wi-Fi.",
                            severity = "ERROR",
                            ssid = snapshot.wifiSsid,
                        )
                    }
            }

            is ContinuityDecision.ShowStatus -> {
                lastDecisionLogKey = null
            }

            ContinuityDecision.Idle -> {
                lastDecisionLogKey = null
            }
        }
    }

    private suspend fun logDecisionOnce(
        key: String,
        category: String,
        message: String,
        severity: String = "INFO",
        ssid: String? = null,
    ) {
        if (lastDecisionLogKey == key) return
        lastDecisionLogKey = key
        eventLogRepository.log(
            category = category,
            message = message,
            severity = severity,
            ssid = ssid,
        )
    }

    private suspend fun logWarmupTransitionIfNeeded(state: CellularWarmupState) {
        if (lastWarmupState == state) return
        lastWarmupState = state
        eventLogRepository.log(
            category = "cellular_state",
            message = when (state) {
                CellularWarmupState.IDLE -> "Cellular warmup released."
                CellularWarmupState.REQUESTING -> "Requesting mobile data warmup."
                CellularWarmupState.AVAILABLE -> "Mobile data warmup is available."
                CellularWarmupState.HOLDING -> "Holding mobile data warmup."
                CellularWarmupState.UNAVAILABLE -> "Mobile data warmup unavailable."
            },
            severity = if (state == CellularWarmupState.UNAVAILABLE) "WARN" else "INFO",
        )
    }

    private fun publishRuntimeState(
        settings: ContinuitySettingsState,
        snapshot: ConnectivitySnapshot,
        warmupState: CellularWarmupState,
        decision: ContinuityDecision,
    ) {
        _runtimeState.update { current ->
            val transitionChanged =
                current.modeEnabled != settings.modeEnabled ||
                    current.snapshot.defaultTransport != snapshot.defaultTransport ||
                    current.snapshot.currentNetworkId != snapshot.currentNetworkId ||
                    current.cellularWarmupState != warmupState

            current.copy(
                modeEnabled = settings.modeEnabled,
                isMonitoring = monitorJob != null,
                snapshot = snapshot,
                wifiSignalBucket = WifiSignalBucket.fromSnapshot(snapshot),
                cellularAvailable = warmupState == CellularWarmupState.AVAILABLE ||
                    warmupState == CellularWarmupState.HOLDING ||
                    snapshot.defaultTransport == io.multinet.mobility.domain.TransportType.CELLULAR,
                cellularWarmupState = warmupState,
                lastDecision = decision,
                lastTransitionAtEpochMillis = if (transitionChanged) {
                    System.currentTimeMillis()
                } else {
                    current.lastTransitionAtEpochMillis
                },
            )
        }
    }

}
