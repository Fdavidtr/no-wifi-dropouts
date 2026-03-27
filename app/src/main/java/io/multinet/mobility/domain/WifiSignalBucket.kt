package io.multinet.mobility.domain

enum class WifiSignalBucket {
    UNKNOWN,
    GOOD,
    WEAK,
    CRITICAL;

    companion object {
        fun fromSnapshot(snapshot: ConnectivitySnapshot): WifiSignalBucket {
            if (snapshot.defaultTransport != TransportType.WIFI) {
                return UNKNOWN
            }

            val rssi = snapshot.rssi ?: return UNKNOWN
            val weakThreshold = when (snapshot.frequencyMhz) {
                in 2400..2500 -> -82
                in 4900..7125 -> -78
                else -> -80
            }

            return when {
                rssi <= weakThreshold - 5 -> CRITICAL
                rssi <= weakThreshold -> WEAK
                else -> GOOD
            }
        }
    }
}

