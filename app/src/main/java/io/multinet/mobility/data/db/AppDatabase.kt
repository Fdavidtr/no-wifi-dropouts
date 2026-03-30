package io.multinet.mobility.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [EventLogEntry::class, SignalSampleEntry::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao

    abstract fun signalSampleDao(): SignalSampleDao
}
