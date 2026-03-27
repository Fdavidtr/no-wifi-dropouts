package io.multinet.mobility.domain

data class ManagedWifiProfile(
    val id: String,
    val ssid: String,
    val securityType: WifiSecurityType,
    val encryptedPassphrase: String,
    val priority: Int,
    val preferredBand: WifiBandPreference,
    val minSignalDbm: Int,
    val allowCellFallback: Boolean,
    val enabled: Boolean,
)

