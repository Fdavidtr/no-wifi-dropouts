package io.multinet.mobility.data

import io.multinet.mobility.domain.ConnectivitySnapshot
import io.multinet.mobility.domain.TransportType
import io.multinet.mobility.domain.WifiSignalBucket

internal const val SIGNAL_SAMPLE_INTERVAL_MILLIS = 5_000L

internal data class SignalSampleState(
    val lastSampleAtEpochMillis: Long? = null,
    val lastSampleSignature: String? = null,
)

internal data class SignalSampleDecision(
    val shouldRecord: Boolean,
    val thresholdRssi: Int?,
    val bucket: WifiSignalBucket,
    val updatedState: SignalSampleState,
)

internal data class NetworkTransitionLogEvent(
    val message: String,
    val severity: String,
    val ssid: String? = null,
)

internal object ContinuityDiagnosticsLogic {
    fun evaluateSignalSample(
        snapshot: ConnectivitySnapshot,
        previousState: SignalSampleState,
    ): SignalSampleDecision {
        val thresholdRssi = WifiSignalBucket.weakThresholdRssi(snapshot)
        val bucket = WifiSignalBucket.fromSnapshot(snapshot)
        val rssi = snapshot.rssi

        if (rssi == null || thresholdRssi == null) {
            return SignalSampleDecision(
                shouldRecord = false,
                thresholdRssi = null,
                bucket = bucket,
                updatedState = previousState,
            )
        }

        val signature = listOf(
            snapshot.currentNetworkId.orEmpty(),
            rssi.toString(),
            thresholdRssi.toString(),
            snapshot.validated.toString(),
        ).joinToString("|")

        val shouldRecord = when {
            previousState.lastSampleAtEpochMillis == null -> true
            previousState.lastSampleSignature != signature -> true
            snapshot.updatedAtEpochMillis - previousState.lastSampleAtEpochMillis >= SIGNAL_SAMPLE_INTERVAL_MILLIS -> true
            else -> false
        }

        return SignalSampleDecision(
            shouldRecord = shouldRecord,
            thresholdRssi = thresholdRssi,
            bucket = bucket,
            updatedState = if (shouldRecord) {
                SignalSampleState(
                    lastSampleAtEpochMillis = snapshot.updatedAtEpochMillis,
                    lastSampleSignature = signature,
                )
            } else {
                previousState
            },
        )
    }

    fun buildNetworkTransitionLogEvent(
        previous: ConnectivitySnapshot,
        current: ConnectivitySnapshot,
    ): NetworkTransitionLogEvent? {
        if (
            previous.defaultTransport == current.defaultTransport &&
            previous.currentNetworkId == current.currentNetworkId
        ) {
            return null
        }

        val message = when {
            current.defaultTransport == TransportType.CELLULAR -> {
                "Default network switched to mobile data."
            }

            current.defaultTransport == TransportType.WIFI &&
                previous.defaultTransport != TransportType.WIFI -> {
                current.wifiSsid?.let { "Default network switched to Wi-Fi ($it)." }
                    ?: "Default network switched to Wi-Fi."
            }

            current.defaultTransport == TransportType.WIFI &&
                previous.currentNetworkId != current.currentNetworkId -> {
                current.wifiSsid?.let { "Wi-Fi network changed to $it." }
                    ?: "Wi-Fi network changed."
            }

            current.defaultTransport == TransportType.NONE -> {
                "No default network is available."
            }

            else -> {
                "Default network changed to ${current.defaultTransport.name.lowercase()}."
            }
        }

        return NetworkTransitionLogEvent(
            message = message,
            severity = if (current.defaultTransport == TransportType.NONE) "WARN" else "INFO",
            ssid = current.wifiSsid,
        )
    }
}
