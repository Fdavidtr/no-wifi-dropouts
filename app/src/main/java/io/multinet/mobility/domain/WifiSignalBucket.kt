package io.multinet.mobility.domain

enum class WifiSignalBucket {
    UNKNOWN,
    GOOD,
    WEAK,
    CRITICAL;

    companion object {
        fun weakThresholdRssi(snapshot: ConnectivitySnapshot): Int? = when {
            snapshot.defaultTransport != TransportType.WIFI -> null
            snapshot.rssi == null -> null
            else -> when (snapshot.frequencyMhz) {
                in 2400..2500 -> -82
                in 4900..7125 -> -78
                else -> -80
            }
        }

        fun fromSnapshot(snapshot: ConnectivitySnapshot): WifiSignalBucket {
            if (snapshot.defaultTransport != TransportType.WIFI) {
                return UNKNOWN
            }

            val rssi = snapshot.rssi ?: return UNKNOWN
            val weakThreshold = weakThresholdRssi(snapshot) ?: return UNKNOWN

            return when {
                rssi <= weakThreshold - 5 -> CRITICAL
                rssi <= weakThreshold -> WEAK
                else -> GOOD
            }
        }
    }
}
