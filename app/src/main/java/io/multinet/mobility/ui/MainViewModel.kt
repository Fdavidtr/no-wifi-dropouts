package io.multinet.mobility.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.multinet.mobility.data.EventLogRepository
import io.multinet.mobility.data.MobilityController
import io.multinet.mobility.data.WifiSuggestionRepository
import io.multinet.mobility.data.preferences.UserPreferencesRepository
import io.multinet.mobility.data.security.CredentialCipher
import io.multinet.mobility.domain.ManagedWifiProfile
import io.multinet.mobility.domain.WifiBandPreference
import io.multinet.mobility.domain.WifiSecurityType
import java.util.UUID
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
    private val mobilityController: MobilityController,
    private val wifiSuggestionRepository: WifiSuggestionRepository,
    private val eventLogRepository: EventLogRepository,
    private val credentialCipher: CredentialCipher,
) : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    val uiState: StateFlow<MainUiState> = combine(
        preferencesRepository.settingsFlow,
        mobilityController.runtimeState,
        eventLogRepository.observeRecent(limit = 100),
    ) { settings, runtime, events ->
        MainUiState(
            settings = settings,
            runtime = runtime,
            events = events,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        viewModelScope.launch {
            mobilityController.refreshCapabilities()
        }
    }

    fun saveProfile(
        existingProfile: ManagedWifiProfile?,
        ssid: String,
        passphrase: String,
        securityType: WifiSecurityType,
        priority: Int,
        preferredBand: WifiBandPreference,
        minSignalDbm: Int,
        allowCellFallback: Boolean,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val encryptedPassphrase = when {
                passphrase.isNotBlank() -> credentialCipher.encrypt(passphrase)
                existingProfile != null -> existingProfile.encryptedPassphrase
                else -> ""
            }

            if (encryptedPassphrase.isBlank()) {
                _messages.tryEmit("La clave Wi-Fi es obligatoria para crear el perfil.")
                return@launch
            }

            val profile = ManagedWifiProfile(
                id = existingProfile?.id ?: UUID.randomUUID().toString(),
                ssid = ssid.trim(),
                securityType = securityType,
                encryptedPassphrase = encryptedPassphrase,
                priority = priority,
                preferredBand = preferredBand,
                minSignalDbm = minSignalDbm,
                allowCellFallback = allowCellFallback,
                enabled = enabled,
            )

            if (profile.ssid.isBlank()) {
                _messages.tryEmit("El SSID no puede estar vacío.")
                return@launch
            }

            existingProfile?.let { current ->
                wifiSuggestionRepository.removeSuggestion(current, disconnectIfActive = false)
            }

            wifiSuggestionRepository.addOrUpdateSuggestion(profile)
                .onSuccess {
                    preferencesRepository.upsertProfile(profile)
                    preferencesRepository.setOnboardingCompleted(true)
                    eventLogRepository.log(
                        category = "profile_saved",
                        message = "Saved managed Wi-Fi profile for ${profile.ssid}.",
                        ssid = profile.ssid,
                    )
                    _messages.tryEmit("Perfil guardado.")
                }
                .onFailure { throwable ->
                    _messages.tryEmit(throwable.message ?: "No se pudo guardar el perfil.")
                }
        }
    }

    fun toggleProfile(profile: ManagedWifiProfile, enabled: Boolean) {
        viewModelScope.launch {
            val updated = profile.copy(enabled = enabled)
            val operation = if (enabled) {
                wifiSuggestionRepository.addOrUpdateSuggestion(updated)
            } else {
                wifiSuggestionRepository.removeSuggestion(updated, disconnectIfActive = false)
            }

            operation.onSuccess {
                preferencesRepository.upsertProfile(updated)
                _messages.tryEmit(if (enabled) "Perfil activado." else "Perfil desactivado.")
            }.onFailure { throwable ->
                _messages.tryEmit(throwable.message ?: "No se pudo cambiar el estado del perfil.")
            }
        }
    }

    fun removeProfile(profile: ManagedWifiProfile) {
        viewModelScope.launch {
            wifiSuggestionRepository.removeSuggestion(profile, disconnectIfActive = false)
            preferencesRepository.removeProfile(profile.id)
            _messages.tryEmit("Perfil eliminado.")
        }
    }
}

