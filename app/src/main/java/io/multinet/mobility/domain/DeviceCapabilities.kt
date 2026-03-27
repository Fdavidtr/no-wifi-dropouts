package io.multinet.mobility.domain

data class DeviceCapabilities(
    val apiLevel: Int,
    val hasConnectivityDiagnostics: Boolean,
    val hasSuggestionApprovalListener: Boolean,
    val supportsMultiInternetWifi: Boolean,
    val multiInternetModeLabel: String,
)

