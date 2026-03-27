package io.multinet.mobility.domain

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolicyEngine @Inject constructor(
    private val clock: Clock,
) {
    private val cooldowns = mutableMapOf<String, Instant>()
    private var invalidSince: Instant? = null
    private var weakSince: Instant? = null

    fun currentCooldowns(now: Instant = Instant.now(clock)): Map<String, Long> {
        pruneCooldowns(now)
        return cooldowns.mapValues { (_, until) -> (until.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0L) }
    }

    fun evaluate(
        snapshot: ConnectivitySnapshot,
        profiles: List<ManagedWifiProfile>,
        candidates: List<ScanCandidate>,
        suggestionsApproved: Boolean,
    ): PolicyDecision {
        val now = Instant.now(clock)
        pruneCooldowns(now)

        val enabledProfiles = profiles.filter { it.enabled }
        val currentProfile = enabledProfiles.firstOrNull { it.ssid == snapshot.wifiSsid }

        if (snapshot.defaultTransport != TransportType.WIFI || currentProfile == null) {
            invalidSince = null
            weakSince = null

            if (snapshot.defaultTransport == TransportType.CELLULAR && suggestionsApproved) {
                val recoverableProfile = enabledProfiles
                    .filterNot { isOnCooldown(it.ssid) }
                    .maxWithOrNull(compareByDescending<ManagedWifiProfile> { it.priority }.thenByDescending { it.minSignalDbm })

                if (recoverableProfile != null) {
                    return PolicyDecision.RestoreWifi(
                        ssid = recoverableProfile.ssid,
                        reason = "Cellular is active and a managed Wi-Fi is eligible again.",
                    )
                }
            }

            return PolicyDecision.Keep
        }

        updateDegradationWindows(snapshot, currentProfile, now)

        val isInvalidLongEnough = invalidSince?.let { now.epochSecond - it.epochSecond >= 5 } == true
        val isWeakLongEnough = weakSince?.let { now.epochSecond - it.epochSecond >= 8 } == true
        val dataStallRecent = snapshot.lastDataStallAtEpochMillis?.let {
            now.toEpochMilli() - it <= 15_000
        } == true || snapshot.dataStallSuspected

        if (!isInvalidLongEnough && !isWeakLongEnough && !dataStallRecent) {
            return PolicyDecision.Keep
        }

        val targetCandidate = candidates
            .filter { it.ssid != currentProfile.ssid }
            .filter { candidate -> enabledProfiles.any { it.ssid == candidate.ssid } }
            .filterNot { isOnCooldown(it.ssid) }
            .maxByOrNull { candidateScore(it, enabledProfiles) }

        val currentRssi = snapshot.rssi ?: Int.MIN_VALUE
        val candidateIsMeaningfullyBetter = targetCandidate != null &&
            (isInvalidLongEnough || targetCandidate.rssi - currentRssi >= 8)

        if (candidateIsMeaningfullyBetter && suggestionsApproved) {
            markCooldown(currentProfile.ssid, 60, now)
            return PolicyDecision.CooldownCurrentWifi(
                ssid = currentProfile.ssid,
                reason = "Current Wi-Fi degraded and ${targetCandidate?.ssid} looks stronger.",
                cooldownSeconds = 60,
                targetCandidateSsid = targetCandidate?.ssid,
            )
        }

        if (currentProfile.allowCellFallback) {
            markCooldown(currentProfile.ssid, 60, now)
            return PolicyDecision.FallbackCellular(
                ssid = currentProfile.ssid,
                reason = "Current Wi-Fi degraded and no better managed Wi-Fi is ready.",
                cooldownSeconds = 60,
            )
        }

        return PolicyDecision.NotifyUser(
            title = "Weak managed Wi-Fi",
            message = "No better managed Wi-Fi is ready and cellular fallback is disabled for ${currentProfile.ssid}.",
        )
    }

    private fun updateDegradationWindows(
        snapshot: ConnectivitySnapshot,
        currentProfile: ManagedWifiProfile,
        now: Instant,
    ) {
        invalidSince = if (!snapshot.validated) invalidSince ?: now else null

        val isWeak = snapshot.rssi?.let { it <= currentProfile.minSignalDbm } == true
        weakSince = if (isWeak) weakSince ?: now else null
    }

    private fun candidateScore(
        candidate: ScanCandidate,
        profiles: List<ManagedWifiProfile>,
    ): Int {
        val profile = profiles.firstOrNull { it.ssid == candidate.ssid } ?: return Int.MIN_VALUE
        return candidate.rssi + (profile.priority * 10)
    }

    private fun markCooldown(ssid: String, seconds: Int, now: Instant) {
        cooldowns[ssid] = now.plusSeconds(seconds.toLong())
    }

    private fun isOnCooldown(ssid: String): Boolean = cooldowns[ssid]?.isAfter(Instant.now(clock)) == true

    private fun pruneCooldowns(now: Instant) {
        cooldowns.entries.removeAll { (_, until) -> !until.isAfter(now) }
    }
}
