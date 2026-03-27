package io.multinet.mobility.domain

data class DeviceCapabilities(
    val apiLevel: Int,
    val hasConnectivityDiagnostics: Boolean,
    val canRequestCellularWarmup: Boolean,
    val canReportBadWifi: Boolean,
)
