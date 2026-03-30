---
name: android-connectivity-device-test
description: Use when working on Wi-Fi degradation, cellular warmup, fallback behavior, or real-device connectivity debugging in this app. Trigger for requests to diagnose networking behavior, design device test steps, assess OEM risk, or review whether product claims match public Android API limits.
---

# Android Connectivity Device Test

Use this skill for behavior work that depends on real Android networking conditions.

## Product constraints

- Keep the framing honest: this app is not a VPN, not link bonding, and not a forced-switch solution.
- Assume OEM variance is real. Behavior that works on one phone may not generalize.
- Prefer public Android API constraints over speculative "magic fix" ideas.

## Investigation flow

1. Identify the layer involved:
   - `ConnectivityRepository` for platform signals and callbacks
   - `ContinuityPolicyEngine` for thresholds and decisions
   - `ContinuityController` for orchestration, deduplication, and logging
   - service, receiver, manifest, and permissions for lifecycle or startup issues
2. Separate emulator-safe work from device-only work. Weak Wi-Fi, validation loss, and cellular fallback timing are primarily device tests.
3. Check whether the issue is a signal problem, a policy problem, or an Android-platform limitation.
4. If thresholds or transitions change, update focused unit tests before relying on manual testing alone.

## Device test checklist

- Baseline on healthy Wi-Fi and confirm that no unnecessary cellular warmup starts.
- Degrade Wi-Fi and observe whether warmup starts after the expected threshold.
- Check whether repeated bad-Wi-Fi reporting is deduplicated per network identity.
- Recover Wi-Fi and confirm warmup release timing.
- Test no-cellular-backup cases and confirm the user-facing status stays honest.
- Compare behavior on at least two device or OEM profiles before making strong claims.

## Review lens

- Prefer battery-aware callback usage over polling.
- Watch for duplicate actions caused by unstable network identity or repeated callbacks.
- Keep UI copy and README claims aligned with what the app can actually influence.

## Reporting

- State whether the conclusion comes from code inspection, unit tests, or real-device observation.
- If a conclusion depends on OEM behavior, say so explicitly.
