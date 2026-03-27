package io.multinet.mobility.domain

data class ConnectivitySnapshot(
    val currentNetworkId: String? = null,
    val defaultTransport: TransportType = TransportType.NONE,
    val validated: Boolean = false,
    val captivePortal: Boolean = false,
    val metered: Boolean = false,
    val internetAvailable: Boolean = false,
    val wifiSsid: String? = null,
    val rssi: Int? = null,
    val frequencyMhz: Int? = null,
    val dataStallSuspected: Boolean = false,
    val lastDataStallAtEpochMillis: Long? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)
