package io.multinet.mobility.domain

sealed interface PolicyDecision {
    data object Keep : PolicyDecision

    data class CooldownCurrentWifi(
        val ssid: String,
        val reason: String,
        val cooldownSeconds: Int,
        val targetCandidateSsid: String? = null,
    ) : PolicyDecision

    data class RestoreWifi(
        val ssid: String,
        val reason: String,
    ) : PolicyDecision

    data class FallbackCellular(
        val ssid: String,
        val reason: String,
        val cooldownSeconds: Int,
    ) : PolicyDecision

    data class NotifyUser(
        val title: String,
        val message: String,
    ) : PolicyDecision
}

