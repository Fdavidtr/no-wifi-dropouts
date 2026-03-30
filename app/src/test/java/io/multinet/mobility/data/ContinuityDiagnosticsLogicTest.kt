package io.multinet.mobility.data

import io.multinet.mobility.domain.ConnectivitySnapshot
import io.multinet.mobility.domain.TransportType
import io.multinet.mobility.domain.WifiSignalBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuityDiagnosticsLogicTest {
    @Test
    fun `records the first wifi sample`() {
        val snapshot = wifiSnapshot(updatedAtEpochMillis = 1_000L)

        val decision = ContinuityDiagnosticsLogic.evaluateSignalSample(
            snapshot = snapshot,
            previousState = SignalSampleState(),
        )

        assertTrue(decision.shouldRecord)
        assertEquals(-78, decision.thresholdRssi)
        assertEquals(WifiSignalBucket.WEAK, decision.bucket)
        assertEquals(1_000L, decision.updatedState.lastSampleAtEpochMillis)
        assertEquals("wifi-1|-79|-78|true", decision.updatedState.lastSampleSignature)
    }

    @Test
    fun `does not record duplicate wifi sample within five seconds`() {
        val snapshot = wifiSnapshot(updatedAtEpochMillis = 4_000L)

        val decision = ContinuityDiagnosticsLogic.evaluateSignalSample(
            snapshot = snapshot,
            previousState = SignalSampleState(
                lastSampleAtEpochMillis = 1_000L,
                lastSampleSignature = "wifi-1|-79|-78|true",
            ),
        )

        assertFalse(decision.shouldRecord)
        assertEquals(1_000L, decision.updatedState.lastSampleAtEpochMillis)
        assertEquals("wifi-1|-79|-78|true", decision.updatedState.lastSampleSignature)
    }

    @Test
    fun `records changed signature within interval`() {
        val changedRssi = ContinuityDiagnosticsLogic.evaluateSignalSample(
            snapshot = wifiSnapshot(updatedAtEpochMillis = 4_000L, rssi = -74),
            previousState = SignalSampleState(
                lastSampleAtEpochMillis = 1_000L,
                lastSampleSignature = "wifi-1|-79|-78|true",
            ),
        )
        val changedNetwork = ContinuityDiagnosticsLogic.evaluateSignalSample(
            snapshot = wifiSnapshot(updatedAtEpochMillis = 4_000L, currentNetworkId = "wifi-2"),
            previousState = SignalSampleState(
                lastSampleAtEpochMillis = 1_000L,
                lastSampleSignature = "wifi-1|-79|-78|true",
            ),
        )
        val changedValidation = ContinuityDiagnosticsLogic.evaluateSignalSample(
            snapshot = wifiSnapshot(updatedAtEpochMillis = 4_000L, validated = false),
            previousState = SignalSampleState(
                lastSampleAtEpochMillis = 1_000L,
                lastSampleSignature = "wifi-1|-79|-78|true",
            ),
        )

        assertTrue(changedRssi.shouldRecord)
        assertTrue(changedNetwork.shouldRecord)
        assertTrue(changedValidation.shouldRecord)
    }

    @Test
    fun `does not record non wifi snapshots`() {
        val decision = ContinuityDiagnosticsLogic.evaluateSignalSample(
            snapshot = ConnectivitySnapshot(
                defaultTransport = TransportType.CELLULAR,
                updatedAtEpochMillis = 2_000L,
            ),
            previousState = SignalSampleState(
                lastSampleAtEpochMillis = 1_000L,
                lastSampleSignature = "wifi-1|-79|-78|true",
            ),
        )

        assertFalse(decision.shouldRecord)
        assertNull(decision.thresholdRssi)
        assertEquals(WifiSignalBucket.UNKNOWN, decision.bucket)
        assertEquals(1_000L, decision.updatedState.lastSampleAtEpochMillis)
    }

    @Test
    fun `logs transition from none to wifi`() {
        val event = ContinuityDiagnosticsLogic.buildNetworkTransitionLogEvent(
            previous = ConnectivitySnapshot(defaultTransport = TransportType.NONE),
            current = wifiSnapshot(wifiSsid = "Office"),
        )

        assertEquals("Default network switched to Wi-Fi (Office).", event?.message)
        assertEquals("INFO", event?.severity)
        assertEquals("Office", event?.ssid)
    }

    @Test
    fun `does not log duplicate transition on same transport and network`() {
        val event = ContinuityDiagnosticsLogic.buildNetworkTransitionLogEvent(
            previous = wifiSnapshot(),
            current = wifiSnapshot(),
        )

        assertNull(event)
    }

    @Test
    fun `logs correct transition messages and severity by transport type`() {
        val wifiChange = ContinuityDiagnosticsLogic.buildNetworkTransitionLogEvent(
            previous = wifiSnapshot(currentNetworkId = "wifi-1", wifiSsid = "Office"),
            current = wifiSnapshot(currentNetworkId = "wifi-2", wifiSsid = "Cafe"),
        )
        val cellular = ContinuityDiagnosticsLogic.buildNetworkTransitionLogEvent(
            previous = wifiSnapshot(),
            current = ConnectivitySnapshot(
                defaultTransport = TransportType.CELLULAR,
                currentNetworkId = "cell-1",
            ),
        )
        val none = ContinuityDiagnosticsLogic.buildNetworkTransitionLogEvent(
            previous = wifiSnapshot(),
            current = ConnectivitySnapshot(defaultTransport = TransportType.NONE),
        )
        val vpn = ContinuityDiagnosticsLogic.buildNetworkTransitionLogEvent(
            previous = wifiSnapshot(),
            current = ConnectivitySnapshot(defaultTransport = TransportType.VPN),
        )

        assertEquals("Wi-Fi network changed to Cafe.", wifiChange?.message)
        assertEquals("INFO", wifiChange?.severity)
        assertEquals("Default network switched to mobile data.", cellular?.message)
        assertEquals("INFO", cellular?.severity)
        assertEquals("No default network is available.", none?.message)
        assertEquals("WARN", none?.severity)
        assertEquals("Default network changed to vpn.", vpn?.message)
        assertEquals("INFO", vpn?.severity)
    }

    private fun wifiSnapshot(
        currentNetworkId: String = "wifi-1",
        wifiSsid: String? = "Office",
        rssi: Int = -79,
        validated: Boolean = true,
        updatedAtEpochMillis: Long = 1_000L,
    ): ConnectivitySnapshot = ConnectivitySnapshot(
        currentNetworkId = currentNetworkId,
        defaultTransport = TransportType.WIFI,
        validated = validated,
        wifiSsid = wifiSsid,
        rssi = rssi,
        frequencyMhz = 5_180,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
