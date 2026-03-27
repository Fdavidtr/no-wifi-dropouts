package io.multinet.mobility.domain

sealed interface ContinuityDecision {
    data object Idle : ContinuityDecision

    data class StartCellularWarmup(
        val reason: String,
    ) : ContinuityDecision

    data class HoldCellularWarmup(
        val reason: String,
    ) : ContinuityDecision

    data class ReleaseCellularWarmup(
        val reason: String,
    ) : ContinuityDecision

    data class ReportBadWifi(
        val reason: String,
    ) : ContinuityDecision

    data class ShowStatus(
        val message: String,
    ) : ContinuityDecision
}

