package io.multinet.mobility.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_samples")
data class SignalSampleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMillis: Long,
    val networkId: String?,
    val ssid: String?,
    val rssi: Int,
    val thresholdRssi: Int,
    val frequencyMhz: Int?,
    val bucket: String,
    val validated: Boolean,
)
