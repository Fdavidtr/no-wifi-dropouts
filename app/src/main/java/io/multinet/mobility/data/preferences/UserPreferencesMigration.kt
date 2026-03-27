package io.multinet.mobility.data.preferences

import androidx.datastore.core.DataMigration
import io.multinet.mobility.datastore.MobilitySettings

object UserPreferencesMigration : DataMigration<MobilitySettings> {
    override suspend fun shouldMigrate(currentData: MobilitySettings): Boolean =
        !currentData.legacyEnabledStateMigrated

    override suspend fun migrate(currentData: MobilitySettings): MobilitySettings =
        migrateLegacyEnabledState(currentData)

    override suspend fun cleanUp() = Unit

    internal fun migrateLegacyEnabledState(currentData: MobilitySettings): MobilitySettings {
        if (currentData.legacyEnabledStateMigrated) return currentData

        return currentData.toBuilder()
            .setModeEnabled(false)
            .setLegacyEnabledStateMigrated(true)
            .build()
    }
}
