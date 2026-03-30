# AGENTS.md

## Repo operating rules

- Target build environment is Android SDK plus JDK `17`. Do not document or validate the app against Java 8.
- Start every change from a dedicated git branch named `feat/...` or `fix/...`; do not develop directly on `master`.
- Use `fix/...` for bug fixes and regressions. Use `feat/...` for feature work, documentation updates, and workflow changes.
- Publish completed work through a GitHub pull request.
- Prefer small scoped changes. Do not mix feature work with broad cleanup.
- Keep README build, install, and validation notes aligned with the real project state.
- Add or update tests when runtime behavior changes, especially for policy timing, persistence, or connectivity transitions.
- Keep policy decisions in domain or data layers. Do not move connectivity decision logic into composables, activities, or receivers.
- Guard Android-version-specific behavior with explicit SDK checks and capability checks before using platform APIs.
- Preserve battery-aware behavior. Avoid polling or repeated callbacks when existing system signals are sufficient.
- Preserve user trust around background behavior. Foreground service use, permissions, and diagnostics visibility should stay explicit in the UI and code.
- Prefer deterministic state transitions over ad hoc heuristics. If a threshold or timeout changes, update focused tests for that branch of the policy.
- When logic depends on a network identity or transition, keep deduplication keyed to the relevant network state so the app does not repeat the same action indefinitely.
- Treat DataStore and Room as compatibility boundaries. When stored data, defaults, or schema shape changes, update migrations and migration tests in the same change.
- Prefer structured event logging through the app's repositories over scattered debug-only logging.

## Recommended validation

- Run `./gradlew testDebugUnitTest` for policy and repository unit tests.
- Run `./gradlew assembleDebug` before shipping app changes.
- Run `./gradlew lintDebug` when UI, manifest, permissions, or Android resource usage changes.
