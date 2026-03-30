---
name: android-build-validate
description: Use when working on this Android app's build, tests, lint, or release-readiness checks. Trigger for requests to validate changes, run the right Gradle tasks, diagnose local build failures, or decide which checks are required for a given Android change.
---

# Android Build Validate

Use this skill for local validation work on `no-wifi-dropouts`.

## Core rules

- Treat JDK 17 as a hard requirement. If `java -version` reports Java 8 or another unsupported runtime, fix that first and do not report Gradle validation as completed.
- Match the validation scope to the change. Do not run heavy checks by default if a smaller check answers the question.
- Do not claim a build, test, or lint result unless the command actually ran in the current environment.

## Validation flow

1. Check the Java runtime with `java -version` when the environment is unknown or Gradle fails early.
2. Choose the minimum useful Gradle commands:
   - `./gradlew testDebugUnitTest` for policy, repository, migration, and pure Kotlin behavior changes
   - `./gradlew lintDebug` for manifest, permissions, services, resources, or Android API usage changes
   - `./gradlew assembleDebug` before closing app changes that could affect packaging or wiring
3. If a command fails, separate environment problems from app regressions. Call out JDK, SDK, or Gradle setup issues explicitly.
4. When behavior changes touch policy thresholds, persistence, or lifecycle wiring, check that focused unit tests exist or add them.

## Repo-specific focus

- `ContinuityPolicyEngine` and its tests are the first stop for timing and decision logic.
- `ConnectivityRepository`, `ContinuityController`, and `ContinuityService` are the first stop for callback, lifecycle, and foreground-service regressions.
- DataStore and Room changes should be treated as compatibility-sensitive and validated with migration coverage where applicable.

## Reporting

- Report exactly which commands ran.
- Report blockers separately from code failures.
- If validation is partial, say what was not run.
