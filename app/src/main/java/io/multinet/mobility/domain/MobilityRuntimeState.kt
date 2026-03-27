package io.multinet.mobility.domain

data class MobilityRuntimeState(
    val isMonitoring: Boolean = false,
    val capabilities: DeviceCapabilities = DeviceCapabilities(
        apiLevel = 0,
        hasConnectivityDiagnostics = false,
        hasSuggestionApprovalListener = false,
        supportsMultiInternetWifi = false,
        multiInternetModeLabel = "Unavailable",
    ),
    val snapshot: ConnectivitySnapshot = ConnectivitySnapshot(),
    val approvalStatus: Int = 0,
    val lastDecision: PolicyDecision = PolicyDecision.Keep,
    val cooldowns: Map<String, Long> = emptyMap(),
)

