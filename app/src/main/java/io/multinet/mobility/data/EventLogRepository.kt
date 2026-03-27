package io.multinet.mobility.data

import io.multinet.mobility.data.db.EventLogDao
import io.multinet.mobility.data.db.EventLogEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class EventLogRepository @Inject constructor(
    private val eventLogDao: EventLogDao,
) {
    fun observeRecent(limit: Int = 500): Flow<List<EventLogEntry>> = eventLogDao.observeRecent(limit)

    suspend fun log(
        category: String,
        message: String,
        severity: String = "INFO",
        ssid: String? = null,
    ) {
        eventLogDao.insert(
            EventLogEntry(
                timestampEpochMillis = System.currentTimeMillis(),
                severity = severity,
                category = category,
                message = message,
                ssid = ssid,
            ),
        )
        eventLogDao.trimToLimit(limit = 500)
    }
}

