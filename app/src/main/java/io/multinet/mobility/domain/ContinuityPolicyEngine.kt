package io.multinet.mobility.domain

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContinuityPolicyEngine @Inject constructor(
    private val clock: Clock,
) {
    private var invalidSince: Instant? = null
    private var weakSince: Instant? = null
    private var healthySince: Instant? = null
    private var lastReportedBadWifiNetworkId: String? = null

    fun evaluate(
        snapshot: ConnectivitySnapshot,
        cellularWarmupState: CellularWarmupState,
        policy: CellularPolicy,
    ): ContinuityDecision {
        val now = Instant.now(clock)
        val signalBucket = WifiSignalBucket.fromSnapshot(snapshot)
        val hasActiveWarmup = cellularWarmupState == CellularWarmupState.REQUESTING ||
            cellularWarmupState == CellularWarmupState.AVAILABLE ||
            cellularWarmupState == CellularWarmupState.HOLDING

        when (snapshot.defaultTransport) {
            TransportType.WIFI -> {
                val invalid = !snapshot.validated || snapshot.captivePortal || !snapshot.internetAvailable
                val weak = signalBucket == WifiSignalBucket.WEAK || signalBucket == WifiSignalBucket.CRITICAL

                if (!invalid && !snapshot.dataStallSuspected) {
                    lastReportedBadWifiNetworkId = null
                }

                invalidSince = if (invalid) invalidSince ?: now else null
                weakSince = if (weak) weakSince ?: now else null
                healthySince = if (!invalid && !weak && !snapshot.dataStallSuspected) {
                    healthySince ?: now
                } else {
                    null
                }

                val invalidDuration = invalidSince?.let { now.epochSecond - it.epochSecond } ?: 0
                val weakDuration = weakSince?.let { now.epochSecond - it.epochSecond } ?: 0
                val healthyDuration = healthySince?.let { now.epochSecond - it.epochSecond } ?: 0

                val shouldWarmCellular = when (policy) {
                    CellularPolicy.BALANCED -> snapshot.dataStallSuspected || invalidDuration >= 3 || weakDuration >= 5
                }

                if ((invalidDuration >= 5 || snapshot.dataStallSuspected) &&
                    snapshot.currentNetworkId != null &&
                    snapshot.currentNetworkId != lastReportedBadWifiNetworkId
                ) {
                    return ContinuityDecision.ReportBadWifi(
                        reason = if (snapshot.dataStallSuspected) {
                            "Wi-Fi data stall suspected."
                        } else {
                            "Wi-Fi is still unvalidated after ${invalidDuration}s."
                        },
                    )
                }

                if (shouldWarmCellular) {
                    return when (cellularWarmupState) {
                        CellularWarmupState.IDLE -> ContinuityDecision.StartCellularWarmup(
                            reason = if (snapshot.dataStallSuspected) {
                                "Wi-Fi looks stalled, starting cellular warmup."
                            } else {
                                "Wi-Fi is at risk, warming up mobile data."
                            },
                        )

                        CellularWarmupState.UNAVAILABLE -> ContinuityDecision.ShowStatus(
                            "Wi-Fi at risk and no mobile backup available.",
                        )

                        CellularWarmupState.REQUESTING,
                        CellularWarmupState.AVAILABLE,
                        CellularWarmupState.HOLDING,
                        -> ContinuityDecision.HoldCellularWarmup(
                            reason = "Keeping mobile data ready while Wi-Fi is unstable.",
                        )
                    }
                }

                if (hasActiveWarmup && healthyDuration >= 45) {
                    return ContinuityDecision.ReleaseCellularWarmup(
                        reason = "Wi-Fi has been healthy for ${healthyDuration}s.",
                    )
                }

                return if (hasActiveWarmup) {
                    ContinuityDecision.HoldCellularWarmup(
                        reason = "Holding mobile data briefly while Wi-Fi stabilizes.",
                    )
                } else {
                    ContinuityDecision.ShowStatus("Wi-Fi stable")
                }
            }

            TransportType.CELLULAR -> {
                invalidSince = null
                weakSince = null
                healthySince = null
                return when (cellularWarmupState) {
                    CellularWarmupState.IDLE,
                    CellularWarmupState.UNAVAILABLE,
                    -> ContinuityDecision.ShowStatus("Using mobile data.")

                    CellularWarmupState.REQUESTING,
                    CellularWarmupState.AVAILABLE,
                    CellularWarmupState.HOLDING,
                    -> ContinuityDecision.HoldCellularWarmup(
                        reason = "Cellular is the default network, keeping the request alive.",
                    )
                }
            }

            TransportType.NONE -> {
                invalidSince = null
                weakSince = null
                healthySince = null
                return when (cellularWarmupState) {
                    CellularWarmupState.IDLE -> ContinuityDecision.StartCellularWarmup(
                        reason = "No default network is available, trying mobile data.",
                    )

                    CellularWarmupState.UNAVAILABLE -> ContinuityDecision.ShowStatus(
                        "No network available and no mobile backup.",
                    )

                    CellularWarmupState.REQUESTING,
                    CellularWarmupState.AVAILABLE,
                    CellularWarmupState.HOLDING,
                    -> ContinuityDecision.HoldCellularWarmup(
                        reason = "No default network is available yet.",
                    )
                }
            }

            else -> {
                invalidSince = null
                weakSince = null
                healthySince = null
                return if (hasActiveWarmup) {
                    ContinuityDecision.HoldCellularWarmup(
                        reason = "Keeping mobile data ready while the default transport changes.",
                    )
                } else {
                    ContinuityDecision.Idle
                }
            }
        }
    }

    fun markBadWifiReported(networkId: String?) {
        lastReportedBadWifiNetworkId = networkId
    }
}
