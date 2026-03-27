package io.multinet.mobility.data.preferences

import io.multinet.mobility.datastore.MobilitySettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesMigrationTest {
    @Test
    fun `clears legacy enabled state during migration`() {
        val legacySettings = MobilitySettings.newBuilder()
            .setModeEnabled(true)
            .build()

        val migrated = UserPreferencesMigration.migrateLegacyEnabledState(legacySettings)

        assertFalse(migrated.modeEnabled)
        assertTrue(migrated.legacyEnabledStateMigrated)
    }

    @Test
    fun `does not reset explicit continuity opt in after migration completed`() {
        val currentSettings = MobilitySettings.newBuilder()
            .setModeEnabled(true)
            .setLegacyEnabledStateMigrated(true)
            .build()

        val migrated = UserPreferencesMigration.migrateLegacyEnabledState(currentSettings)

        assertTrue(migrated.modeEnabled)
        assertTrue(migrated.legacyEnabledStateMigrated)
    }
}
