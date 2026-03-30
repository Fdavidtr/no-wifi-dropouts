package io.multinet.mobility.data

import io.multinet.mobility.data.db.SignalSampleDao
import io.multinet.mobility.data.db.SignalSampleEntry
import io.multinet.mobility.domain.ConnectivitySnapshot
import io.multinet.mobility.domain.WifiSignalBucket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SignalHistoryRepository @Inject constructor(
    private val signalSampleDao: SignalSampleDao,
) {
    fun observeRecent(limit: Int = 180): Flow<List<SignalSampleEntry>> = signalSampleDao.observeRecent(limit)

    suspend fun record(
        snapshot: ConnectivitySnapshot,
        thresholdRssi: Int,
        bucket: WifiSignalBucket,
    ) {
        val rssi = snapshot.rssi ?: return
        signalSampleDao.insert(
            SignalSampleEntry(
                timestampEpochMillis = snapshot.updatedAtEpochMillis,
                networkId = snapshot.currentNetworkId,
                ssid = snapshot.wifiSsid,
                rssi = rssi,
                thresholdRssi = thresholdRssi,
                frequencyMhz = snapshot.frequencyMhz,
                bucket = bucket.name,
                validated = snapshot.validated,
            ),
        )
        signalSampleDao.trimToLimit(limit = 720)
    }
}
