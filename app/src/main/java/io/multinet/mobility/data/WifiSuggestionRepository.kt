package io.multinet.mobility.data

import android.annotation.SuppressLint
import android.net.wifi.BlockingOption
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import io.multinet.mobility.data.security.CredentialCipher
import io.multinet.mobility.domain.ManagedWifiProfile
import io.multinet.mobility.domain.ScanCandidate
import io.multinet.mobility.domain.WifiBandPreference
import io.multinet.mobility.domain.WifiSecurityType
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class WifiSuggestionRepository @Inject constructor(
    private val wifiManager: WifiManager,
    private val credentialCipher: CredentialCipher,
    private val callbackExecutor: Executor,
) {
    private val _approvalStatus = MutableStateFlow(WifiManager.STATUS_SUGGESTION_APPROVAL_UNKNOWN)
    val approvalStatus: StateFlow<Int> = _approvalStatus.asStateFlow()

    private val _connectionFailures = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val connectionFailures: SharedFlow<String> = _connectionFailures.asSharedFlow()

    private var started = false

    private val approvalListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        WifiManager.SuggestionUserApprovalStatusListener { status ->
            _approvalStatus.value = status
        }
    } else {
        null
    }

    private val connectionListener = WifiManager.SuggestionConnectionStatusListener { suggestion, failureReason ->
        val ssid = suggestion.ssid ?: "unknown"
        _connectionFailures.tryEmit("$ssid failed with reason code $failureReason")
    }

    fun startMonitoring() {
        if (started) return
        started = true
        wifiManager.addSuggestionConnectionStatusListener(callbackExecutor, connectionListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && approvalListener != null) {
            wifiManager.addSuggestionUserApprovalStatusListener(callbackExecutor, approvalListener)
        }
    }

    fun stopMonitoring() {
        if (!started) return
        wifiManager.removeSuggestionConnectionStatusListener(connectionListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && approvalListener != null) {
            wifiManager.removeSuggestionUserApprovalStatusListener(approvalListener)
        }
        started = false
    }

    @SuppressLint("MissingPermission")
    fun addOrUpdateSuggestion(profile: ManagedWifiProfile): Result<Unit> = runCatching {
        val status = wifiManager.addNetworkSuggestions(listOf(buildSuggestion(profile)))
        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS &&
            status != WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE
        ) {
            error("Unable to add Wi-Fi suggestion for ${profile.ssid}. Status=$status")
        }
    }

    @SuppressLint("MissingPermission")
    fun removeSuggestion(profile: ManagedWifiProfile, disconnectIfActive: Boolean = true): Result<Unit> = runCatching {
        val suggestion = buildSuggestion(profile)
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val action = if (disconnectIfActive) {
                WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT
            } else {
                WifiManager.ACTION_REMOVE_SUGGESTION_LINGER
            }
            wifiManager.removeNetworkSuggestions(listOf(suggestion), action)
        } else {
            wifiManager.removeNetworkSuggestions(listOf(suggestion))
        }

        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS &&
            status != WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID
        ) {
            error("Unable to remove Wi-Fi suggestion for ${profile.ssid}. Status=$status")
        }
    }

    fun suppressCurrentSuggestion(profile: ManagedWifiProfile, blockingSeconds: Int): Result<Boolean> = runCatching {
        if (Build.VERSION.SDK_INT >= 36) {
            val option = BlockingOption.Builder(blockingSeconds)
                .setBlockingBssidOnly(false)
                .build()
            wifiManager.disallowCurrentSuggestedNetwork(option)
            false
        } else {
            removeSuggestion(profile, disconnectIfActive = true).getOrThrow()
            true
        }
    }

    fun restoreSuggestion(profile: ManagedWifiProfile): Result<Unit> = addOrUpdateSuggestion(profile)

    fun isApprovalGranted(status: Int): Boolean = status == WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER ||
        status == WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE

    @SuppressLint("MissingPermission")
    fun visibleCandidates(): List<ScanCandidate> = wifiManager.scanResults
        .orEmpty()
        .asSequence()
        .filter { it.SSID.isNotBlank() }
        .groupBy { it.SSID }
        .mapNotNull { (_, candidates) -> candidates.maxByOrNull(ScanResult::level) }
        .map {
            ScanCandidate(
                ssid = it.SSID,
                rssi = it.level,
                band = WifiBandPreference.fromFrequency(it.frequency),
            )
        }
        .sortedByDescending(ScanCandidate::rssi)
        .toList()

    private fun buildSuggestion(profile: ManagedWifiProfile): WifiNetworkSuggestion {
        val passphrase = credentialCipher.decryptOrNull(profile.encryptedPassphrase)
            ?: error("Cannot decrypt Wi-Fi passphrase for ${profile.ssid}")

        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(profile.ssid)
            .setPriority(profile.priority)
            .setIsInitialAutojoinEnabled(profile.enabled)

        when (profile.securityType) {
            WifiSecurityType.WPA2 -> builder.setWpa2Passphrase(passphrase)
            WifiSecurityType.WPA3 -> builder.setWpa3Passphrase(passphrase)
        }

        return builder.build()
    }
}

