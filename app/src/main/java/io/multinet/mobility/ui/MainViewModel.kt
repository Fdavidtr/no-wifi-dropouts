package io.multinet.mobility.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.multinet.mobility.data.ContinuityController
import io.multinet.mobility.data.EventLogRepository
import io.multinet.mobility.data.SignalHistoryRepository
import io.multinet.mobility.data.preferences.UserPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val continuityController: ContinuityController,
    private val eventLogRepository: EventLogRepository,
    private val signalHistoryRepository: SignalHistoryRepository,
) : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val uiState: StateFlow<MainUiState> = combine(
        preferencesRepository.settingsFlow,
        continuityController.runtimeState,
        eventLogRepository.observeRecent(limit = 20),
        signalHistoryRepository.observeRecent(limit = 180),
    ) { settings, runtime, events, signalSamples ->
        MainUiState(
            settings = settings,
            runtime = runtime,
            events = events,
            signalSamples = signalSamples,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        viewModelScope.launch {
            continuityController.refreshCapabilities()
        }
    }

    fun markIntroCompleted() {
        viewModelScope.launch {
            preferencesRepository.setIntroCompleted(true)
        }
    }

    fun setDiagnosticsUnlocked(unlocked: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDiagnosticsUnlocked(unlocked)
            _messages.tryEmit(
                if (unlocked) {
                    "Advanced diagnostics visible."
                } else {
                    "Advanced diagnostics hidden."
                },
            )
        }
    }
}
