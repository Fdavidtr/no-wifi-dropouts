package io.multinet.mobility.domain

data class MobilitySettingsState(
    val mobilityModeEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val profiles: List<ManagedWifiProfile> = emptyList(),
)

