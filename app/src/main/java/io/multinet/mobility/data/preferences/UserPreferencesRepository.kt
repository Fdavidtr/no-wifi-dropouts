package io.multinet.mobility.data.preferences

import androidx.datastore.core.DataStore
import io.multinet.mobility.datastore.CellularPolicy as ProtoCellularPolicy
import io.multinet.mobility.datastore.MobilitySettings
import io.multinet.mobility.domain.CellularPolicy
import io.multinet.mobility.domain.ContinuitySettingsState
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
    val settingsFlow: Flow<ContinuitySettingsState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(MobilitySettings.getDefaultInstance())
            } else {
                throw exception
            }
        }
        .map { it.toDomain() }

    suspend fun currentSettings(): ContinuitySettingsState = settingsFlow.first()

    suspend fun setModeEnabled(enabled: Boolean) {
        dataStore.updateData {
            it.toBuilder()
                .setModeEnabled(enabled)
                .build()
        }
    }

    suspend fun setIntroCompleted(completed: Boolean) {
        dataStore.updateData {
            it.toBuilder()
                .setIntroCompleted(completed)
                .build()
        }
    }

    suspend fun setDiagnosticsUnlocked(unlocked: Boolean) {
        dataStore.updateData {
            it.toBuilder()
                .setDiagnosticsUnlocked(unlocked)
                .build()
        }
    }

    suspend fun setCellularPolicy(policy: CellularPolicy) {
        dataStore.updateData {
            it.toBuilder()
                .setCellularPolicy(policy.toProto())
                .build()
        }
    }

    private fun MobilitySettings.toDomain(): ContinuitySettingsState = ContinuitySettingsState(
        modeEnabled = modeEnabled,
        introCompleted = introCompleted,
        cellularPolicy = when (cellularPolicy) {
            ProtoCellularPolicy.CELLULAR_POLICY_BALANCED,
            ProtoCellularPolicy.CELLULAR_POLICY_UNSPECIFIED,
            ProtoCellularPolicy.UNRECOGNIZED,
            -> CellularPolicy.BALANCED
        },
        diagnosticsUnlocked = diagnosticsUnlocked,
    )

    private fun CellularPolicy.toProto(): ProtoCellularPolicy = when (this) {
        CellularPolicy.BALANCED -> ProtoCellularPolicy.CELLULAR_POLICY_BALANCED
    }
}
