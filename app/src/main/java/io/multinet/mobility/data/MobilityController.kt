package io.multinet.mobility.data

import android.net.wifi.WifiManager
import io.multinet.mobility.di.ApplicationScope
import io.multinet.mobility.domain.ManagedWifiProfile
import io.multinet.mobility.domain.MobilityRuntimeState
import io.multinet.mobility.domain.PolicyDecision
import io.multinet.mobility.domain.TransportType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.update

@Singleton
class MobilityController @Inject constructor(
    private val capabilityProbe: CapabilityProbe,
    private val connectivityRepository: ConnectivityRepository,
    private val wifiSuggestionRepository: WifiSuggestionRepository,
    private val userPreferencesRepository: io.multinet.mobility.data.preferences.UserPreferencesRepository,
    private val eventLogRepository: EventLogRepository,
    private val policyEngine: io.multinet.mobility.domain.PolicyEngine,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val restoreJobs = mutableMapOf<String, Job>()
    private var monitorJob: Job? = null

    private val _runtimeState = MutableStateFlow(
        MobilityRuntimeState(
            capabilities = capabilityProbe.probe(),
        ),
    )
    val runtimeState: StateFlow<MobilityRuntimeState> = _runtimeState.asStateFlow()

    suspend fun refreshCapabilities() {
        _runtimeState.update { current ->
            current.copy(capabilities = capabilityProbe.probe())
        }
    }

    suspend fun startMonitoring() = mutex.withLock {
        if (monitorJob != null) return

        refreshCapabilities()
        connectivityRepository.startMonitoring()
        wifiSuggestionRepository.startMonitoring()

        monitorJob = applicationScope.launch {
            launch {
                wifiSuggestionRepository.connectionFailures.collect { failure ->
                    eventLogRepository.log(
                        category = "wifi_suggestion_failure",
                        severity = "WARN",
                        message = failure,
                    )
                }
            }

            combine(
                connectivityRepository.snapshot,
                userPreferencesRepository.settingsFlow,
                wifiSuggestionRepository.approvalStatus,
            ) { snapshot, settings, approvalStatus ->
                Triple(snapshot, settings, approvalStatus)
            }.collect { (snapshot, settings, approvalStatus) ->
                val cooldowns = policyEngine.currentCooldowns()
                _runtimeState.value = _runtimeState.value.copy(
                    isMonitoring = true,
                    snapshot = snapshot,
                    approvalStatus = approvalStatus,
                    cooldowns = cooldowns,
                )

                if (!settings.mobilityModeEnabled) {
                    return@collect
                }

                val decision = policyEngine.evaluate(
                    snapshot = snapshot,
                    profiles = settings.profiles,
                    candidates = wifiSuggestionRepository.visibleCandidates(),
                    suggestionsApproved = wifiSuggestionRepository.isApprovalGranted(approvalStatus),
                )
                applyDecision(decision, settings.profiles)
            }
        }

        eventLogRepository.log(
            category = "mobility_controller",
            message = "Mobility monitoring started.",
        )
    }

    suspend fun stopMonitoring() = mutex.withLock {
        restoreJobs.values.forEach(Job::cancel)
        restoreJobs.clear()
        monitorJob?.cancel()
        monitorJob = null
        connectivityRepository.stopMonitoring()
        wifiSuggestionRepository.stopMonitoring()
        _runtimeState.update { current ->
            current.copy(
                isMonitoring = false,
                lastDecision = PolicyDecision.Keep,
                cooldowns = emptyMap(),
            )
        }
        eventLogRepository.log(
            category = "mobility_controller",
            message = "Mobility monitoring stopped.",
        )
    }

    private suspend fun applyDecision(
        decision: PolicyDecision,
        profiles: List<ManagedWifiProfile>,
    ) {
        if (decision == PolicyDecision.Keep) {
            _runtimeState.update { it.copy(lastDecision = decision, cooldowns = policyEngine.currentCooldowns()) }
            return
        }

        when (decision) {
            is PolicyDecision.CooldownCurrentWifi -> {
                val profile = profiles.firstOrNull { it.ssid == decision.ssid } ?: return
                if (restoreJobs.containsKey(profile.ssid)) return

                val needsRestore = wifiSuggestionRepository
                    .suppressCurrentSuggestion(profile, decision.cooldownSeconds)
                    .getOrElse { throwable ->
                        eventLogRepository.log(
                            category = "policy_error",
                            severity = "ERROR",
                            ssid = profile.ssid,
                            message = throwable.message ?: "Unknown suppression failure.",
                        )
                        return
                    }

                eventLogRepository.log(
                    category = "policy",
                    severity = "WARN",
                    ssid = profile.ssid,
                    message = decision.reason,
                )

                if (needsRestore) {
                    scheduleRestore(profile, decision.cooldownSeconds)
                }
            }

            is PolicyDecision.FallbackCellular -> {
                val profile = profiles.firstOrNull { it.ssid == decision.ssid } ?: return
                if (restoreJobs.containsKey(profile.ssid)) return

                val needsRestore = wifiSuggestionRepository
                    .suppressCurrentSuggestion(profile, decision.cooldownSeconds)
                    .getOrElse { throwable ->
                        eventLogRepository.log(
                            category = "policy_error",
                            severity = "ERROR",
                            ssid = profile.ssid,
                            message = throwable.message ?: "Unknown fallback failure.",
                        )
                        return
                    }

                eventLogRepository.log(
                    category = "fallback",
                    severity = "WARN",
                    ssid = profile.ssid,
                    message = decision.reason,
                )

                if (needsRestore) {
                    scheduleRestore(profile, decision.cooldownSeconds)
                }
            }

            is PolicyDecision.RestoreWifi -> {
                val profile = profiles.firstOrNull { it.ssid == decision.ssid } ?: return
                if (restoreJobs.containsKey(profile.ssid)) return
                if (_runtimeState.value.snapshot.defaultTransport != TransportType.CELLULAR) return

                wifiSuggestionRepository.restoreSuggestion(profile).onSuccess {
                    eventLogRepository.log(
                        category = "restore_wifi",
                        message = decision.reason,
                        ssid = profile.ssid,
                    )
                }.onFailure { throwable ->
                    eventLogRepository.log(
                        category = "policy_error",
                        severity = "ERROR",
                        ssid = profile.ssid,
                        message = throwable.message ?: "Unknown restore failure.",
                    )
                }
            }

            is PolicyDecision.NotifyUser -> {
                eventLogRepository.log(
                    category = "notification",
                    severity = "WARN",
                    message = "${decision.title}: ${decision.message}",
                )
            }

            PolicyDecision.Keep -> Unit
        }

        _runtimeState.update { current ->
            current.copy(lastDecision = decision, cooldowns = policyEngine.currentCooldowns())
        }
    }

    private fun scheduleRestore(profile: ManagedWifiProfile, cooldownSeconds: Int) {
        restoreJobs[profile.ssid] = applicationScope.launch {
            delay(cooldownSeconds * 1_000L)
            wifiSuggestionRepository.restoreSuggestion(profile).onSuccess {
                eventLogRepository.log(
                    category = "restore_wifi",
                    message = "Restored ${profile.ssid} suggestion after cooldown.",
                    ssid = profile.ssid,
                )
            }.onFailure { throwable ->
                eventLogRepository.log(
                    category = "policy_error",
                    severity = "ERROR",
                    ssid = profile.ssid,
                    message = throwable.message ?: "Unknown delayed restore failure.",
                )
            }
            restoreJobs.remove(profile.ssid)
            _runtimeState.update { current ->
                current.copy(cooldowns = policyEngine.currentCooldowns())
            }
        }
    }

    fun approvalStatusLabel(status: Int): String = when (status) {
        WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER -> "Approved"
        WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE -> "Carrier approved"
        WifiManager.STATUS_SUGGESTION_APPROVAL_REJECTED_BY_USER -> "Rejected"
        WifiManager.STATUS_SUGGESTION_APPROVAL_PENDING -> "Pending"
        else -> "Unknown"
    }
}

