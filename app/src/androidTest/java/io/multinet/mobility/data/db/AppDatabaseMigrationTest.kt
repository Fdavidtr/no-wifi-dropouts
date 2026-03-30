package io.multinet.mobility.data.db

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesEventLogRowsAndCreatesSignalSamplesTable() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO event_log_entries
                    (timestampEpochMillis, severity, category, message, ssid)
                VALUES
                    (1710000000000, 'WARN', 'bad_wifi_report', 'Wi-Fi degraded.', 'Office')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            AppDatabase_AutoMigration_1_2_Impl(),
        ).use { database ->
            assertEquals(1, queryCount(database, "SELECT COUNT(*) FROM event_log_entries"))
            assertEquals(1, queryCount(database, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'signal_samples'"))
            assertTrue(
                queryCount(database, "SELECT COUNT(*) FROM pragma_table_info('signal_samples') WHERE name = 'thresholdRssi'") == 1,
            )
        }
    }

    private fun queryCount(
        database: SupportSQLiteDatabase,
        sql: String,
    ): Int = database.query(sql).useSingleInt()

    private fun Cursor.useSingleInt(): Int = use {
        assertTrue(moveToFirst())
        getInt(0)
    }

    private companion object {
        private const val TEST_DB = "app-database-migration-test"
    }
}
