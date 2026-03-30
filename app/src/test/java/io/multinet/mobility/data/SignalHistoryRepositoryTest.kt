package io.multinet.mobility.data

import io.multinet.mobility.data.db.SignalSampleDao
import io.multinet.mobility.data.db.SignalSampleEntry
import io.multinet.mobility.domain.ConnectivitySnapshot
import io.multinet.mobility.domain.TransportType
import io.multinet.mobility.domain.WifiSignalBucket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalHistoryRepositoryTest {
    @Test
    fun `skips snapshot with null rssi`() = runBlocking {
        val dao = FakeSignalSampleDao()
        val repository = SignalHistoryRepository(dao)

        repository.record(
            snapshot = ConnectivitySnapshot(
                defaultTransport = TransportType.WIFI,
                updatedAtEpochMillis = 1_000L,
            ),
            thresholdRssi = -78,
            bucket = WifiSignalBucket.UNKNOWN,
        )

        assertNull(dao.insertedEntry)
        assertNull(dao.trimLimit)
    }

    @Test
    fun `inserts signal sample and trims history`() = runBlocking {
        val dao = FakeSignalSampleDao()
        val repository = SignalHistoryRepository(dao)

        repository.record(
            snapshot = ConnectivitySnapshot(
                currentNetworkId = "wifi-1",
                defaultTransport = TransportType.WIFI,
                validated = true,
                wifiSsid = "Office",
                rssi = -77,
                frequencyMhz = 5_180,
                updatedAtEpochMillis = 1_000L,
            ),
            thresholdRssi = -78,
            bucket = WifiSignalBucket.WEAK,
        )

        assertEquals(
            SignalSampleEntry(
                timestampEpochMillis = 1_000L,
                networkId = "wifi-1",
                ssid = "Office",
                rssi = -77,
                thresholdRssi = -78,
                frequencyMhz = 5_180,
                bucket = "WEAK",
                validated = true,
            ),
            dao.insertedEntry,
        )
        assertEquals(720, dao.trimLimit)
    }

    private class FakeSignalSampleDao : SignalSampleDao {
        var insertedEntry: SignalSampleEntry? = null
        var trimLimit: Int? = null

        override fun observeRecent(limit: Int): Flow<List<SignalSampleEntry>> = flowOf(emptyList())

        override suspend fun insert(entry: SignalSampleEntry) {
            insertedEntry = entry
        }

        override suspend fun trimToLimit(limit: Int) {
            trimLimit = limit
        }
    }
}
