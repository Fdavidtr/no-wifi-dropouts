package io.multinet.mobility.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuityPolicyEngineTest {
    @Test
    fun `starts cellular warmup after wifi stays invalid for three seconds`() {
        val clock = MutableClock()
        val engine = ContinuityPolicyEngine(clock)
        val snapshot = wifiSnapshot(validated = false, internetAvailable = true)

        engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.IDLE,
            policy = CellularPolicy.BALANCED,
        )
        clock.advanceSeconds(4)

        val decision = engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.IDLE,
            policy = CellularPolicy.BALANCED,
        )

        assertTrue(decision is ContinuityDecision.StartCellularWarmup)
    }

    @Test
    fun `reports bad wifi after five seconds of invalid connectivity`() {
        val clock = MutableClock()
        val engine = ContinuityPolicyEngine(clock)
        val snapshot = wifiSnapshot(validated = false, internetAvailable = true)

        engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.REQUESTING,
            policy = CellularPolicy.BALANCED,
        )
        clock.advanceSeconds(6)

        val decision = engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.REQUESTING,
            policy = CellularPolicy.BALANCED,
        )

        assertTrue(decision is ContinuityDecision.ReportBadWifi)
    }

    @Test
    fun `does not report the same wifi twice until it recovers`() {
        val clock = MutableClock()
        val engine = ContinuityPolicyEngine(clock)
        val snapshot = wifiSnapshot(validated = false, internetAvailable = true, currentNetworkId = "wifi-1")

        engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.REQUESTING,
            policy = CellularPolicy.BALANCED,
        )
        clock.advanceSeconds(6)
        val firstDecision = engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.REQUESTING,
            policy = CellularPolicy.BALANCED,
        )
        engine.markBadWifiReported(snapshot.currentNetworkId)

        val secondDecision = engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.REQUESTING,
            policy = CellularPolicy.BALANCED,
        )

        assertTrue(firstDecision is ContinuityDecision.ReportBadWifi)
        assertTrue(secondDecision is ContinuityDecision.HoldCellularWarmup)
    }

    @Test
    fun `releases cellular warmup after wifi is healthy for forty five seconds`() {
        val clock = MutableClock()
        val engine = ContinuityPolicyEngine(clock)
        val snapshot = wifiSnapshot(validated = true, internetAvailable = true, rssi = -62, frequencyMhz = 5180)

        engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.HOLDING,
            policy = CellularPolicy.BALANCED,
        )
        clock.advanceSeconds(46)

        val decision = engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.HOLDING,
            policy = CellularPolicy.BALANCED,
        )

        assertTrue(decision is ContinuityDecision.ReleaseCellularWarmup)
    }

    @Test
    fun `shows missing mobile backup when cellular warmup is unavailable`() {
        val clock = MutableClock()
        val engine = ContinuityPolicyEngine(clock)
        val snapshot = wifiSnapshot(validated = false, internetAvailable = true)

        engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.UNAVAILABLE,
            policy = CellularPolicy.BALANCED,
        )
        clock.advanceSeconds(4)

        val decision = engine.evaluate(
            snapshot = snapshot,
            cellularWarmupState = CellularWarmupState.UNAVAILABLE,
            policy = CellularPolicy.BALANCED,
        )

        assertEquals(
            ContinuityDecision.ShowStatus("Wi-Fi at risk and no mobile backup available."),
            decision,
        )
    }

    private fun wifiSnapshot(
        validated: Boolean,
        internetAvailable: Boolean,
        currentNetworkId: String = "wifi-default",
        rssi: Int = -76,
        frequencyMhz: Int = 5180,
    ): ConnectivitySnapshot = ConnectivitySnapshot(
        currentNetworkId = currentNetworkId,
        defaultTransport = TransportType.WIFI,
        validated = validated,
        internetAvailable = internetAvailable,
        wifiSsid = "Casa",
        rssi = rssi,
        frequencyMhz = frequencyMhz,
    )

    private class MutableClock(
        private var current: Instant = Instant.parse("2026-03-27T10:00:00Z"),
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant(): Instant = current

        fun advanceSeconds(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }
}
