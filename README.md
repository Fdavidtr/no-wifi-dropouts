# No WiFi Dropouts

Native Android app that reduces Wi-Fi dropouts at home by warming up mobile data and nudging Android to fall back faster when Wi-Fi starts to degrade.

It also warns you when Wi-Fi starts to degrade or when mobile data backup is unavailable.

## The Problem

Phone connectivity at home is often good enough until you move between rooms, floors, or weak Wi-Fi zones. The common failure mode is not a full disconnect at first, but a sticky, degrading Wi-Fi connection that takes too long to fail over to mobile data.

This project explores a narrow and realistic goal:

- detect when the current Wi-Fi connection is becoming unreliable
- warm up mobile data before the connection fully collapses
- signal bad Wi-Fi to the Android framework so fallback can happen faster
- keep the product intentionally simple with a one-tap UI

## What The App Does

When enabled, the app runs a foreground monitoring service and watches the default network using Android system callbacks.

If Wi-Fi becomes unstable, the app:

- observes degraded connectivity signals such as loss of validation, suspected data stall, or sustained weak RSSI
- requests a cellular network in advance so mobile data is ready if the Wi-Fi path deteriorates further
- reports bad connectivity on the current Wi-Fi network so Android can reevaluate the default route
- releases the mobile backup once Wi-Fi has been healthy again for a sustained window

The user-facing product is deliberately minimal:

- one `Connect` / `Disconnect` button
- one status line
- one hidden diagnostics panel for recent events and runtime state

## What It Does Not Do

This is the most important section for understanding the scope of the project.

The app does **not**:

- do VPN-based bonding or link aggregation
- guarantee multi-Wi-Fi simultaneous connectivity
- force Android to switch networks unconditionally
- turn mobile data on if it is disabled at the system level
- bypass OEM, radio, or permission limitations

This is a local continuity and accelerated fallback tool, not a networking magic trick.

## How It Works

At a high level, the continuity loop is:

1. register for default network and diagnostics callbacks
2. build a live connectivity snapshot from system state
3. evaluate whether Wi-Fi is stable, weak, invalid, or stalled
4. request `CELLULAR + INTERNET` when Wi-Fi risk crosses a threshold
5. call `reportNetworkConnectivity(network, false)` when Wi-Fi remains unhealthy
6. keep the cellular request alive while the fallback is in progress
7. release the cellular warmup when Wi-Fi has recovered for long enough

Default policy in the current implementation:

- warm up cellular after roughly 3 seconds of invalid Wi-Fi or 5 seconds of sustained weak signal
- report bad Wi-Fi when degradation persists or a data stall is suspected
- keep mobile backup alive while cellular is the default network
- release the mobile request after Wi-Fi has remained healthy for 45 seconds

## Architecture

The project is structured around a few focused components:

- `ConnectivityRepository`
  Reads the default network via `ConnectivityManager.registerDefaultNetworkCallback()` and enriches it with diagnostics when available.
- `ContinuityPolicyEngine`
  Encodes the fallback policy and decides when to warm up mobile data, hold it, release it, or report bad Wi-Fi.
- `CellularWarmupRepository`
  Owns the `requestNetwork(CELLULAR + INTERNET)` lifecycle and tracks whether mobile backup is idle, requesting, available, holding, or unavailable.
- `ContinuityController`
  Combines runtime connectivity, settings, and warmup state; applies decisions; and writes event logs.
- `ContinuityService`
  Runs the monitoring loop in a foreground service so the feature stays active while enabled.

Relevant files:

- `app/src/main/java/io/multinet/mobility/data/ConnectivityRepository.kt`
- `app/src/main/java/io/multinet/mobility/domain/ContinuityPolicyEngine.kt`
- `app/src/main/java/io/multinet/mobility/data/CellularWarmupRepository.kt`
- `app/src/main/java/io/multinet/mobility/data/ContinuityController.kt`
- `app/src/main/java/io/multinet/mobility/service/ContinuityService.kt`
- `app/src/main/java/io/multinet/mobility/ui/MultiNetApp.kt`

## Technical Choices

- **Native Kotlin**: this project lives close to Android networking APIs, permissions, lifecycle, and foreground service behavior. A native stack keeps the control surface honest.
- **Jetpack Compose**: the UI is intentionally minimal and state-driven.
- **Coroutines + Flow**: the app is event-driven rather than polling-based.
- **Hilt**: clean dependency boundaries between repositories, policy, controller, and service.
- **Proto DataStore**: lightweight structured settings.
- **Room**: small local event log for diagnostics.

## Validation

Local validation completed:

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

There is dedicated coverage for the continuity policy engine, including:

- cellular warmup on degraded Wi-Fi
- reporting bad Wi-Fi after sustained invalid connectivity
- avoiding repeated reports for the same Wi-Fi until it recovers
- releasing warmup after healthy recovery
- surfacing missing mobile backup conditions

## Real-World Constraints

This project is intentionally honest about Android limitations.

Behavior depends on:

- device OEM behavior
- Android version
- granted permissions
- Wi-Fi chipset and radio behavior
- whether mobile data is actually available

Important caveats:

- `requestNetwork(...)` is a request, not absolute control over the network stack
- `reportNetworkConnectivity(..., false)` is a signal to the framework, not a guaranteed switch
- diagnostics APIs are not equally available or equally reliable across all devices
- mobile fallback behavior is partly owned by Android itself, not just the app

## Why This Is Portfolio-Worthy

This project is interesting because it focuses on engineering restraint.

Instead of pretending to solve impossible networking problems on Android, it:

- identifies a realistic connectivity pain point
- works within public Android APIs
- makes explicit tradeoffs around battery, scope, and system control
- keeps the UI minimal and puts the complexity where it belongs: policy and system integration
- documents what is and is not technically possible

## Next Steps

The most useful next steps are:

- validate behavior on multiple OEM devices and document differences
- add screenshots or a short demo GIF
- capture structured metrics for fallback timing in real home-network tests
- add instrumented tests where feasible
- prepare a release build and GitHub release assets for demonstration
- rename the default branch from `master` to `main` for a cleaner public repo setup

## Build

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
