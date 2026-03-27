package io.multinet.mobility.domain

data class ContinuitySettingsState(
    val modeEnabled: Boolean = false,
    val introCompleted: Boolean = false,
    val cellularPolicy: CellularPolicy = CellularPolicy.BALANCED,
    val diagnosticsUnlocked: Boolean = false,
)

