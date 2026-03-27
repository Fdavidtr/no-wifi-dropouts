# No WiFi Dropouts

Android app that tries to reduce Wi-Fi dropouts by warming up mobile data when Wi-Fi starts to degrade.

The idea is simple: keep the product honest, local, and narrow. This is not a VPN, not bonding, and not a fake "magic internet" demo. It is a native Android experiment focused on one thing: making fallback from weak Wi-Fi to mobile data faster and more predictable.

## What it does

- Watches the current network with Android system callbacks
- Detects when Wi-Fi starts to become unstable
- Requests mobile data in advance as a backup path
- Signals bad Wi-Fi to the Android framework so fallback can happen sooner
- Shows a minimal one-button UI plus a small diagnostics panel

It also warns you when Wi-Fi starts to degrade or when mobile data backup is unavailable.

## What it does not do

- No VPN
- No link bonding
- No guaranteed multi-Wi-Fi support
- No forced network switching
- No promises that every Android device will behave the same

That limitation is part of the point of the project: work with public Android APIs, not against them.

## Tech

- Kotlin
- Jetpack Compose
- Coroutines + Flow
- Hilt
- Proto DataStore
- Room

Core Android APIs used:

- `ConnectivityManager`
- `ConnectivityDiagnosticsManager`
- `requestNetwork(CELLULAR + INTERNET)`
- `reportNetworkConnectivity(..., false)`
- foreground service + runtime permissions

## Why this project is interesting

Most connectivity ideas become nonsense as soon as they hit real Android constraints.

This project is interesting because it stays inside those constraints and still tries to improve a real problem:

- sticky weak Wi-Fi
- slow fallback to mobile data
- OEM-dependent network behavior
- battery-conscious monitoring without aggressive polling

## Validation

Local validation completed:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

It was also tested on my own Android phone. The improvement was limited, which ended up being one of the main takeaways of the project: Android puts real constraints on how much a normal app can influence Wi-Fi-to-mobile fallback behavior.

That was still valuable. The project worked well as a learning exercise in Android connectivity, system callbacks, foreground services, permissions, and the practical limits of public networking APIs.

## Next steps

- Test on more real devices and compare OEM behavior
- Add screenshots or a short demo GIF
- Measure fallback timing on real networks
- Prepare a cleaner public release setup
