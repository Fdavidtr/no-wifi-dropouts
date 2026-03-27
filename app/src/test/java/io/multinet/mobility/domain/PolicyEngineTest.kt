package io.multinet.mobility.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {
    @Test
    fun keepsCurrentWifiWhenNetworkIsHealthy() = runTest {
        val clock = MutableClock(Instant.parse("2026-03-27T10:00:00Z"))
        val engine = PolicyEngine(clock)
        val profile = managedProfile(ssid = "Casa-5G", priority = 100, minSignalDbm = -80)

        val decision = engine.evaluate(
            snapshot = ConnectivitySnapshot(
                defaultTransport = TransportType.WIFI,
                wifiSsid = "Casa-5G",
                validated = true,
                rssi = -65,
            ),
            profiles = listOf(profile),
            candidates = emptyList(),
            suggestionsApproved = true,
        )

        assertEquals(PolicyDecision.Keep, decision)
    }

    @Test
    fun coolsDownCurrentWifiWhenValidationDropsAndBetterCandidateExists() = runTest {
        val clock = MutableClock(Instant.parse("2026-03-27T10:00:00Z"))
        val engine = PolicyEngine(clock)
        val current = managedProfile(ssid = "Casa-5G", priority = 100, minSignalDbm = -80)
        val backup = managedProfile(ssid = "Casa-2G", priority = 90, minSignalDbm = -85)

        engine.evaluate(
            snapshot = ConnectivitySnapshot(
                defaultTransport = TransportType.WIFI,
                wifiSsid = current.ssid,
                validated = false,
                rssi = -83,
            ),
            profiles = listOf(current, backup),
            candidates = listOf(ScanCandidate(ssid = backup.ssid, rssi = -60, band = WifiBandPreference.BAND_2_4_GHZ)),
            suggestionsApproved = true,
        )

        clock.advanceSeconds(6)

        val decision = engine.evaluate(
            snapshot = ConnectivitySnapshot(
                defaultTransport = TransportType.WIFI,
                wifiSsid = current.ssid,
                validated = false,
                rssi = -83,
            ),
            profiles = listOf(current, backup),
            candidates = listOf(ScanCandidate(ssid = backup.ssid, rssi = -60, band = WifiBandPreference.BAND_2_4_GHZ)),
            suggestionsApproved = true,
        )

        assertTrue(decision is PolicyDecision.CooldownCurrentWifi)
        assertEquals("Casa-5G", (decision as PolicyDecision.CooldownCurrentWifi).ssid)
    }

    @Test
    fun restoresHighestPriorityWifiWhenCellularIsActive() = runTest {
        val clock = MutableClock(Instant.parse("2026-03-27T10:00:00Z"))
        val engine = PolicyEngine(clock)
        val lower = managedProfile(ssid = "Casa-2G", priority = 80, minSignalDbm = -85)
        val higher = managedProfile(ssid = "Casa-5G", priority = 100, minSignalDbm = -80)

        val decision = engine.evaluate(
            snapshot = ConnectivitySnapshot(
                defaultTransport = TransportType.CELLULAR,
                validated = true,
            ),
            profiles = listOf(lower, higher),
            candidates = emptyList(),
            suggestionsApproved = true,
        )

        assertEquals(
            PolicyDecision.RestoreWifi(
                ssid = "Casa-5G",
                reason = "Cellular is active and a managed Wi-Fi is eligible again.",
            ),
            decision,
        )
    }

    private fun managedProfile(
        ssid: String,
        priority: Int,
        minSignalDbm: Int,
    ): ManagedWifiProfile = ManagedWifiProfile(
        id = ssid,
        ssid = ssid,
        securityType = WifiSecurityType.WPA2,
        encryptedPassphrase = "encrypted",
        priority = priority,
        preferredBand = WifiBandPreference.ANY,
        minSignalDbm = minSignalDbm,
        allowCellFallback = true,
        enabled = true,
    )
}

private class MutableClock(
    private var current: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current

    fun advanceSeconds(seconds: Long) {
        current = current.plusSeconds(seconds)
    }
}
