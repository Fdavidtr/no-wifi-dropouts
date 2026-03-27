package io.multinet.mobility.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_log_entries")
data class EventLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMillis: Long,
    val severity: String,
    val category: String,
    val message: String,
    val ssid: String? = null,
)

