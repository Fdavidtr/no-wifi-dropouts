package io.multinet.mobility.ui

import io.multinet.mobility.data.db.EventLogEntry
import io.multinet.mobility.data.db.SignalSampleEntry
import io.multinet.mobility.domain.ContinuityRuntimeState
import io.multinet.mobility.domain.ContinuitySettingsState

data class MainUiState(
    val settings: ContinuitySettingsState = ContinuitySettingsState(),
    val runtime: ContinuityRuntimeState = ContinuityRuntimeState(),
    val events: List<EventLogEntry> = emptyList(),
    val signalSamples: List<SignalSampleEntry> = emptyList(),
)
