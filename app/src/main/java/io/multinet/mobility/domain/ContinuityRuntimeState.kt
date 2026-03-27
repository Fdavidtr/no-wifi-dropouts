package io.multinet.mobility.domain

data class ContinuityRuntimeState(
    val modeEnabled: Boolean = false,
    val isMonitoring: Boolean = false,
    val capabilities: DeviceCapabilities = DeviceCapabilities(
        apiLevel = 0,
        hasConnectivityDiagnostics = false,
        canRequestCellularWarmup = false,
        canReportBadWifi = false,
    ),
    val snapshot: ConnectivitySnapshot = ConnectivitySnapshot(),
    val wifiSignalBucket: WifiSignalBucket = WifiSignalBucket.UNKNOWN,
    val cellularAvailable: Boolean = false,
    val cellularWarmupState: CellularWarmupState = CellularWarmupState.IDLE,
    val lastDecision: ContinuityDecision = ContinuityDecision.Idle,
    val lastTransitionAtEpochMillis: Long? = null,
)

