package io.multinet.mobility.ui

import io.multinet.mobility.data.db.EventLogEntry
import io.multinet.mobility.domain.MobilityRuntimeState
import io.multinet.mobility.domain.MobilitySettingsState

data class MainUiState(
    val settings: MobilitySettingsState = MobilitySettingsState(),
    val runtime: MobilityRuntimeState = MobilityRuntimeState(),
    val events: List<EventLogEntry> = emptyList(),
)

