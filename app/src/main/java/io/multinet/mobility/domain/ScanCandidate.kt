package io.multinet.mobility.domain

data class ScanCandidate(
    val ssid: String,
    val rssi: Int,
    val band: WifiBandPreference,
)

