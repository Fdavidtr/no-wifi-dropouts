package io.multinet.mobility.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalSampleDao {
    @Query("SELECT * FROM signal_samples ORDER BY timestampEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SignalSampleEntry>>

    @Insert
    suspend fun insert(entry: SignalSampleEntry)

    @Query(
        """
        DELETE FROM signal_samples
        WHERE id NOT IN (
            SELECT id FROM signal_samples
            ORDER BY timestampEpochMillis DESC
            LIMIT :limit
        )
        """,
    )
    suspend fun trimToLimit(limit: Int)
}
