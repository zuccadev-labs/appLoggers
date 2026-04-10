---
name: applogger-sdk-live-configuration
description: Configure AppLogger SDK in a real project environment by inspecting local.properties, adding only missing keys, and validating end-to-end initialization.
---

# AppLogger SDK Live Configuration

## When to use this skill

Use this skill when the user asks for practical, in-environment SDK setup completion (not only conceptual guidance).

Examples:

1. "termina de configurar el sdk"
2. "revisa local.properties y ajusta lo faltante"
3. "deja el sdk operativo con validacion"

## Mandatory constraints

1. Read `local.properties` if present.
2. If `local.properties` is missing, create from `local.properties.example` only when requested.
3. Add only missing AppLogger keys.
4. Never rename/delete unrelated existing keys.
5. Never print full secrets in output.
6. Use canonical imports only: `com.applogger.core.*` and `com.applogger.transport.supabase.SupabaseTransport`.
7. Never suggest `com.applogger.sdk.*` imports.
8. Never assume `BuildConfig.LOGGER_*` symbols exist; verify first and map only if missing.
9. Never use Android entrypoint `AppLoggerSDK` for iOS KMP paths; use `AppLoggerIos.shared` in Kotlin.
10. Resolve the target SDK version before touching dependency declarations; prefer the latest tagged release and confirm it against `sdk/gradle.properties`.
11. Preserve a single authoritative config path for AppLogger credentials when possible; avoid duplicating endpoint/key across `BuildConfig`, packaged assets, and other runtime files without a real need.
12. If the consumer app has an `Application` class, prefer initializing AppLogger there before background services, receivers, workers, or embedded servers emit logs.
13. If the app needs pre-auth correlation, set a pseudonymous identity after initialization and let authenticated flows update identity later.
14. Do not treat a successful live setup as complete unless there is at least one startup log and one health snapshot check.

## Required SDK config keys

1. `APPLOGGER_URL`
2. `APPLOGGER_ANON_KEY`
3. `APPLOGGER_DEBUG`

## Optional SDK config keys

1. `APPLOGGER_BETA_TESTER` — boolean (`true`/`false`). Activates beta tester mode. The tester's email is NOT a config key — it comes from the developer's auth flow at runtime via `AppLoggerSDK.setBetaTester(email)`.
2. `APPLOGGER_CAPTURE_CALLER_INFO` — optional boolean only when forensic caller capture is explicitly required. Do not enable by default in performance-sensitive apps.

## Debug output behavior

1. Logcat output on Android appears only when `isDebugMode=true` and `consoleOutput=true`.
2. In standard setups, `APPLOGGER_DEBUG` maps to both `debugMode` and `consoleOutput`, so `APPLOGGER_DEBUG=true` enables Logcat output.
3. No additional Logcat configuration, logging wrapper, or Android logger setup is required.
4. Never set `APPLOGGER_DEBUG=true` in production builds.

Source of truth in AppLoggers SDK:

1. `sdk/logger-core/src/commonMain/kotlin/com/applogger/core/internal/AppLoggerImpl.kt` — console emission guard is `if (config.isDebugMode && config.consoleOutput)`.
2. `sdk/logger-core/src/commonMain/kotlin/com/applogger/core/AppLoggerConfig.kt` — Builder default is `consoleOutput = true`.

## Workflow

1. Detect project target and module wiring.
2. Resolve the SDK version to install.
3. Inspect `local.properties` and `local.properties.example`.
4. Add missing AppLogger keys only.
5. Validate Gradle mapping to BuildConfig.
6. Validate SDK initialization path.
7. Run build/test smoke checks.
8. Report remaining manual inputs (if secrets are unavailable).

## Mature live-setup pattern for Android consumers

1. Map AppLogger values into `BuildConfig` from `local.properties` or the project's existing secure config source.
2. Construct `SupabaseTransport` explicitly with Android network availability wiring.
3. Initialize from `Application.onCreate()` or a dedicated initializer object called there.
4. In debug builds, run `config.validate()` and surface issues without blocking development.
5. After `initialize()`, set the pseudonymous device identity if needed and inject `app_package` globally.
6. Emit a startup BOOT log and confirm `AppLoggerHealth.snapshot().isInitialized` and `transportAvailable`.
7. If the app later authenticates a user, update telemetry correlation from the auth flow instead of baking user identifiers into static config.

## Version resolution for live setup

1. Preferred source: latest SDK release tag in GitHub.
2. Repository fallback: `sdk/gradle.properties` → `VERSION_NAME`.
3. Keep all AppLoggers artifacts aligned to that same version.

## Syntax impact for existing integrations

1. Existing initialization syntax remains valid.
2. `captureCallerInfo` is additive and optional.
3. Telemetry-schema consumers should prefer top-level `app_package` / `source_scope` instead of legacy JSON keys.
4. Existing consumers that initialize AppLogger inside a service or feature module should usually be consolidated to `Application`-level bootstrap unless there is a hard process-boundary reason not to.

## Validation commands

1. `cd sdk && ./gradlew check`
2. `cd sdk && ./gradlew assemble`

## Gaps outside automation

1. Real secret values if the user has not provided them.
2. Supabase dashboard access to obtain URL/anon key.

## References bundled with this skill

1. `references/local-properties-live-checklist.md`

## Output standard

1. Explicit list of keys found/missing/added.
2. Exact files touched.
3. Validation status and blockers.
4. Safe next steps for unresolved secrets.
