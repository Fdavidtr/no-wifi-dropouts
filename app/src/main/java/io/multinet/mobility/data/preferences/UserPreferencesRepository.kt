package io.multinet.mobility.data.preferences

import androidx.datastore.core.DataStore
import io.multinet.mobility.datastore.MobilitySettings
import io.multinet.mobility.datastore.SecurityType
import io.multinet.mobility.datastore.WifiBand
import io.multinet.mobility.domain.ManagedWifiProfile
import io.multinet.mobility.domain.MobilitySettingsState
import io.multinet.mobility.domain.WifiBandPreference
import io.multinet.mobility.domain.WifiSecurityType
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<MobilitySettings>,
) {
    val settingsFlow: Flow<MobilitySettingsState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(MobilitySettings.getDefaultInstance())
            } else {
                throw exception
            }
        }
        .map { it.toDomain() }

    suspend fun currentSettings(): MobilitySettingsState = settingsFlow.first()

    suspend fun setMobilityModeEnabled(enabled: Boolean) {
        dataStore.updateData {
            it.toBuilder()
                .setMobilityModeEnabled(enabled)
                .build()
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.updateData {
            it.toBuilder()
                .setOnboardingCompleted(completed)
                .build()
        }
    }

    suspend fun upsertProfile(profile: ManagedWifiProfile) {
        dataStore.updateData { settings ->
            val updatedProfiles = settings.wifiProfilesList
                .filterNot { it.id == profile.id }
                .plus(profile.toProto())
                .sortedByDescending { it.priority }

            settings.toBuilder()
                .clearWifiProfiles()
                .addAllWifiProfiles(updatedProfiles)
                .build()
        }
    }

    suspend fun removeProfile(profileId: String) {
        dataStore.updateData { settings ->
            val remaining = settings.wifiProfilesList.filterNot { it.id == profileId }
            settings.toBuilder()
                .clearWifiProfiles()
                .addAllWifiProfiles(remaining)
                .build()
        }
    }

    private fun MobilitySettings.toDomain(): MobilitySettingsState = MobilitySettingsState(
        mobilityModeEnabled = mobilityModeEnabled,
        onboardingCompleted = onboardingCompleted,
        profiles = wifiProfilesList
            .map { protoProfile ->
                ManagedWifiProfile(
                    id = protoProfile.id,
                    ssid = protoProfile.ssid,
                    securityType = when (protoProfile.securityType) {
                        SecurityType.SECURITY_TYPE_WPA3 -> WifiSecurityType.WPA3
                        else -> WifiSecurityType.WPA2
                    },
                    encryptedPassphrase = protoProfile.encryptedPassphrase,
                    priority = protoProfile.priority,
                    preferredBand = when (protoProfile.preferredBand) {
                        WifiBand.WIFI_BAND_2_4_GHZ -> WifiBandPreference.BAND_2_4_GHZ
                        WifiBand.WIFI_BAND_5_GHZ -> WifiBandPreference.BAND_5_GHZ
                        WifiBand.WIFI_BAND_6_GHZ -> WifiBandPreference.BAND_6_GHZ
                        else -> WifiBandPreference.ANY
                    },
                    minSignalDbm = protoProfile.minSignalDbm,
                    allowCellFallback = protoProfile.allowCellFallback,
                    enabled = protoProfile.enabled,
                )
            }
            .sortedByDescending { it.priority },
    )

    private fun ManagedWifiProfile.toProto(): io.multinet.mobility.datastore.WifiProfile =
        io.multinet.mobility.datastore.WifiProfile.newBuilder()
            .setId(id)
            .setSsid(ssid)
            .setSecurityType(
                when (securityType) {
                    WifiSecurityType.WPA2 -> SecurityType.SECURITY_TYPE_WPA2
                    WifiSecurityType.WPA3 -> SecurityType.SECURITY_TYPE_WPA3
                },
            )
            .setEncryptedPassphrase(encryptedPassphrase)
            .setPriority(priority)
            .setPreferredBand(
                when (preferredBand) {
                    WifiBandPreference.ANY -> WifiBand.WIFI_BAND_ANY
                    WifiBandPreference.BAND_2_4_GHZ -> WifiBand.WIFI_BAND_2_4_GHZ
                    WifiBandPreference.BAND_5_GHZ -> WifiBand.WIFI_BAND_5_GHZ
                    WifiBandPreference.BAND_6_GHZ -> WifiBand.WIFI_BAND_6_GHZ
                },
            )
            .setMinSignalDbm(minSignalDbm)
            .setAllowCellFallback(allowCellFallback)
            .setEnabled(enabled)
            .build()
}

