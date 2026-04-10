---
name: applogger-project-integration
description: Inspect an existing Kotlin or Kotlin Multiplatform app, identify the best integration points for AppLogger, and wire the SDK into the project safely. Use when the user says things like "lee la app e integra el sdk", "analiza el proyecto y agrega AppLogger", or "dime dónde inicializar y usar el logger".
---

# AppLogger Project Integration

## When to use this skill

Use this skill when the user wants the agent to:

1. Read the existing app structure.
2. Decide where AppLogger should be initialized.
3. Integrate the SDK with minimal disruption.
4. Add representative logging calls in the right places.

Do not use this skill when:

1. The user only wants a step-by-step installation guide.
2. The target project is not Kotlin or KMP based.
3. The request is about backend schema work instead of client integration.

## Mandatory constraints

1. Inspect the project before proposing code changes.
2. Reuse the app's existing architecture patterns.
3. Avoid broad invasive changes on the first pass.
4. Keep iOS guidance KMP-only.
5. If `local.properties` is used, verify required AppLogger keys first.
6. Add only missing AppLogger keys and keep all existing unrelated variables untouched.
7. Use canonical SDK packages only: `com.applogger.core.*` and `com.applogger.transport.supabase.SupabaseTransport`.
8. Never generate `com.applogger.sdk.*` imports.
9. Validate Logcat guidance with the exact condition: `isDebugMode && consoleOutput`.
10. Never assume `BuildConfig.LOGGER_*` exists; verify symbols first or add mapping explicitly.
11. Never use Android entrypoint `AppLoggerSDK` in iOS KMP integration code.
12. For iOS KMP integration, use `AppLoggerIos.shared.initialize(...)` from Kotlin.
13. Device fingerprint is automatically captured on Android — SHA-256 pseudonymized. Do not log raw `ANDROID_ID`.
14. When integrating beta tester mode, use `APPLOGGER_BETA_TESTER=true` as boolean flag. Email comes from the developer's auth flow at runtime via `AppLoggerSDK.setBetaTester(email)`.
15. Remote config requires the `device_remote_config` table in Supabase (migration 013). Verify migrations are applied before enabling.
16. Inject `app_package` global extra automatically for multi-app identification on the same device.
17. Resolve the target SDK version before editing the consumer project: prefer the latest tagged GitHub release, then confirm it against `sdk/gradle.properties`.
18. Preserve existing logging syntax when possible; the current telemetry-contract changes are additive, not a breaking API rewrite.
19. For Android apps with services, workers, or servers, prefer a single initializer object invoked from `Application.onCreate()` instead of scattered per-service initialization.
20. Standardize tags through a central constants object or facade. Do not build tags dynamically at call sites.
21. If the app starts anonymously and authenticates later, allow a two-step identity model: pseudonymous device identity first, then authenticated user correlation at runtime.
22. Do not log tokens, raw device identifiers, or stable user identifiers in message strings. Put only non-sensitive dimensions in `extra` or metrics tags.
23. For long-lived services or headless apps, emit a final health snapshot and call `AppLoggerSDK.flush()` during controlled shutdown paths.
24. Do not duplicate AppLogger credentials across multiple runtime config surfaces unless the app genuinely needs them in both places.

## Workflow

1. Identify modules, entry points, and platform targets.
2. Detect current logging, crash reporting, and configuration patterns.
3. Resolve the version the consumer must use.
4. Inspect `local.properties` usage and required AppLogger keys.
5. Add only missing AppLogger keys without modifying unrelated variables.
6. Choose the correct initialization point.
7. Add AppLogger dependencies and bootstrap code.
8. Add a small set of high-value logging points.
9. Add a health check or smoke validation path.
10. Run build/tests when available.

## Forensic Android pattern from a real consumer app

Use this pattern when the target looks like a headless Android service app, TV app, or local server app:

1. Create a dedicated initializer object such as `AppLoggerInitializer`.
2. Construct `SupabaseTransport` explicitly with `androidNetworkAvailabilityProvider(context)`.
3. Build `AppLoggerConfig` once, including `environment`, `debugMode`, `consoleOutput`, `minLevel`, buffering strategy, and remote config only if the backend contract exists.
4. In development builds, call `config.validate()` before `initialize()` and surface issues through Logcat.
5. Immediately after initialization, set a pseudonymous identity if the app has a stable device fingerprint or equivalent non-PII identifier.
6. Add `app_package` as a global extra once so multi-app device analysis stays queryable.
7. Register a crash handler only if the app does not already have one, and always chain to the previous handler.
8. Emit a BOOT log plus an initial `AppLoggerHealth.snapshot()` log after successful initialization.
9. In `Application.onCreate()`, initialize the SDK before starting workers, foreground services, or embedded servers that will log during boot.
10. In service shutdown paths, log uptime/health and call `AppLoggerSDK.flush()` before process teardown.

## Version resolution for downstream projects

1. Preferred source: latest GitHub release/tag for the artifact line being consumed.
2. Repository fallback: `sdk/gradle.properties` → `VERSION_NAME`.
3. Keep `logger-core`, `logger-transport-supabase`, and `logger-test` on the same version.
4. Replace placeholders like `<latest-version>` with the resolved concrete version before leaving committed changes.

## Consumer compatibility notes

1. Base SDK initialization and log calls remain valid; no breaking syntax migration is required for `AppLoggerConfig.Builder`, `AppLoggerSDK.info/error/metric`, or the common extension helpers.
2. New telemetry capabilities are additive:
	- top-level `app_package`
	- top-level `source_scope`
	- optional forensic `source_file` / `source_method`
	- optional `captureCallerInfo(true)` when caller forensics are explicitly needed
3. If the consumer previously queried Supabase directly, update filters from legacy JSON paths such as `extra->>'package_name'` to top-level columns like `app_package` and `source_scope`.

## Correct-use guidance derived from forensic audit

1. Good pattern: central bootstrap in `Application` with a dedicated initializer object.
2. Good pattern: domain tags centralized in one file such as `BOOT`, `AUTH`, `API`, `GRPC`, `FILTER`, `SYSTEM`, `CRASH`.
3. Good pattern: combine logs with domain metrics, for example latency metrics tagged with `reason`, `action`, or `status`.
4. Good pattern: start with pseudonymous device identity, then switch correlation to the authenticated user when auth succeeds.
5. Improve if seen: remove PII from freeform messages even if the same identifier is also set via SDK identity APIs.
6. Improve if seen: avoid noisy emoji-heavy or mixed-language production messages when the logs will feed grep, CLI filters, alerts, or downstream analytics.
7. Improve if seen: do not keep the same AppLogger secret in both packaged asset files and `BuildConfig` unless there is an explicit runtime reason.
8. Improve if seen: if the consumer uses outdated AppLogger artifact versions, upgrade all AppLogger artifacts together in one aligned change.

## References bundled with this skill

1. `references/integration-playbook.md`
2. `references/android-patterns.md`
3. `references/ios-kmp-patterns.md`
4. `references/klinema-forensic-pattern.md`

## Output standard

1. Explain why each integration point was chosen.
2. Distinguish required changes from optional improvements.
3. Call out assumptions and unknowns.
4. End with a short validation plan.
