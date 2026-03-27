package io.multinet.mobility.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_log_entries ORDER BY timestampEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventLogEntry>>

    @Insert
    suspend fun insert(entry: EventLogEntry)

    @Query(
        """
        DELETE FROM event_log_entries
        WHERE id NOT IN (
            SELECT id FROM event_log_entries
            ORDER BY timestampEpochMillis DESC
            LIMIT :limit
        )
        """,
    )
    suspend fun trimToLimit(limit: Int)
}

