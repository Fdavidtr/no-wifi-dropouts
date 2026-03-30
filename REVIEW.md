# Review guidance

Prioritize findings that could cause runtime regressions, incorrect connectivity decisions, or misleading product behavior on real Android devices.

Focus on:

- Platform safety: Android API usage must be guarded by the right SDK checks, capability checks, permission flow, and foreground-service expectations.
- Policy correctness: changes in `ContinuityPolicyEngine` or controller flow must preserve timing thresholds, warmup release behavior, and duplicate bad-Wi-Fi reporting rules.
- Lifecycle safety: repositories, callbacks, and services must register and unregister cleanly so monitoring does not leak, stall, or survive in the wrong state.
- Persistence safety: DataStore defaults, migrations, protobuf fields, Room entities, and event logging changes must stay compatible with existing installs.
- Product honesty: UI copy and README claims must stay aligned with the real Android constraints of the app and must not imply unsupported networking behavior.
- Battery awareness: avoid new polling loops, excessive logging, or unnecessary long-lived work when platform callbacks already provide the signal.
- Test coverage: behavior changes should add or update focused tests, especially around thresholds, migrations, and state transitions.

Lower priority:

- Internal refactors with no behavior change
- Cosmetic Compose cleanups that do not affect state handling or user trust
- Tooling-only or workflow-only edits, unless they break the developer validation flow
