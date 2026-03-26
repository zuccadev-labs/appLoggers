# Integration Playbook

## What to inspect first

1. Root and module Gradle files.
2. Android `Application` class or DI bootstrap.
3. Shared KMP module entry points.
4. Existing logging, analytics, or crash SDKs.
5. Local configuration sources such as `local.properties`, env files, or `BuildConfig`.

## Preferred initialization points

1. Android: `Application.onCreate()` or the main DI/bootstrap layer.
2. KMP iOS: shared Kotlin bootstrap invoked from app startup code.
3. Shared business logic: use `AppLoggerExtensions` (`Any.logD/I/W/E/C`) in `commonMain` for repeated usage — tag is inferred automatically from the class name.

## First-pass integration policy

1. Add dependencies.
2. Initialize once.
3. Add one startup log.
4. Add one network or error log.
5. Add one health-check path.

## local.properties handling rule

1. Detect whether the project uses `local.properties` for runtime config.
2. Verify required AppLogger keys.
3. Add only missing AppLogger keys.
4. Do not change unrelated keys, comments, ordering, or existing values.

Required keys:

1. `APPLOGGER_URL`
2. `APPLOGGER_ANON_KEY`
3. `APPLOGGER_DEBUG`

## Canonical imports and packages

Use only these package roots in Android integration code:

1. `com.applogger.core.AppLoggerSDK`
2. `com.applogger.core.AppLoggerConfig`
3. `com.applogger.core.AppLoggerHealth`
4. `com.applogger.transport.supabase.SupabaseTransport`

Do not use `com.applogger.sdk.*` imports.

Platform API mapping (must match SDK source):

1. Android entry point: `com.applogger.core.AppLoggerSDK`
2. iOS KMP entry point: `com.applogger.core.AppLoggerIos.shared`
3. Shared health snapshot: `com.applogger.core.AppLoggerHealth.snapshot()`

Do not cross these entry points between platforms.

## Canonical initialization snippet (Android)

```kotlin
val transport = SupabaseTransport(
    endpoint = BuildConfig.LOGGER_URL,
    apiKey = BuildConfig.LOGGER_KEY,
    networkAvailabilityProvider = androidNetworkAvailabilityProvider(this)
)

AppLoggerSDK.initialize(
    context = this,
    config = AppLoggerConfig.Builder()
        .endpoint(BuildConfig.LOGGER_URL)
        .apiKey(BuildConfig.LOGGER_KEY)
        .environment("production")          // "production" | "staging" | "development"
        .debugMode(BuildConfig.LOGGER_DEBUG)
        .consoleOutput(BuildConfig.LOGGER_DEBUG)
        .minLevel(LogMinLevel.INFO)          // descarta DEBUG en producción
        .batchSize(20)
        .flushIntervalSeconds(30)
        .remoteConfigEnabled(true)          // activa polling de config remota
        .remoteConfigIntervalSeconds(300)
        .build(),
    transport = transport
)
```

Before applying this snippet, verify that `BuildConfig.LOGGER_URL`, `BuildConfig.LOGGER_KEY`, and `BuildConfig.LOGGER_DEBUG` exist. If they do not exist, map values from your current config source first.

## Canonical initialization snippet (iOS KMP)

```kotlin
val config = AppLoggerConfig.Builder()
    .endpoint(url)
    .apiKey(anonKey)
    .environment("production")          // "production" | "staging" | "development"
    .debugMode(debugMode)
    .consoleOutput(debugMode)
    .minLevel(LogMinLevel.INFO)         // descarta DEBUG en producción
    .batchSize(20)
    .flushIntervalSeconds(30)
    .build()

val transport = SupabaseTransport(endpoint = url, apiKey = anonKey)

AppLoggerIos.shared.initialize(config = config, transport = transport)
// Iniciar polling de config remota (opcional)
AppLoggerIos.shared.startRemoteConfig(intervalSeconds = 300)
```

Logcat visibility rule: output is shown only when `isDebugMode=true` and `consoleOutput=true`.

## Device fingerprint and remote config

The SDK automatically captures a persistent, pseudonymized device fingerprint:
`SHA-256(ANDROID_ID + ":" + package_name)`.

### What the developer needs to know

1. **No setup required** — fingerprint is captured and attached to every event automatically.
2. **Read it** via `AppLoggerSDK.getDeviceFingerprint()` (Android) after `initialize()`.
3. **Where it lives in Supabase**: `app_logs.extra->>'device_fingerprint'` (JSONB field).
4. **Remote config table**: `device_remote_config.device_fingerprint` column links to this hash.

### Remote config checklist

1. Add `.remoteConfigEnabled(true)` to `AppLoggerConfig.Builder`.
2. Optionally set `.remoteConfigIntervalSeconds(300)` (default 5 min, range 30-3600).
3. Run migration 013 (`device_remote_config` table) on Supabase.
4. Use CLI `apploggers remote-config set --fingerprint <hash> --debug true` to control devices.
5. ERROR/CRITICAL events are **never filtered** by remote config — they always pass.

### Database tables involved

| Table | Column | Purpose |
|---|---|---|
| `app_logs` | `extra->>'device_fingerprint'` | JSONB field on every event |
| `device_remote_config` | `device_fingerprint` | Config rule key (NULL = global) |

### Required migration

Migration 013 (`docs/ES/migraciones/013_device_remote_config.sql`) must be applied to Supabase.

## Beta tester integration

For apps distributed via Play Store beta/internal testing tracks:

1. Add `APPLOGGER_BETA_TESTER=true` to `local.properties` (beta builds only).
2. Map it to `BuildConfig.IS_BETA_TESTER` (boolean) in `build.gradle`.
3. The **developer** captures the tester's email from their own auth flow (Google Sign-In, Firebase, custom login, etc.).
4. Call `AppLoggerSDK.setBetaTester(email)` with the captured email at runtime.
5. The SDK injects `is_beta_tester=true` and `beta_tester_email` into every event.
6. Query beta tester events: `WHERE extra->>'is_beta_tester' = 'true'`.
7. Erase tester data: `apploggers erase --user-id "tester@example.com"`.

Important: the email is NOT a config variable — each tester's email is captured at runtime
from the developer's auth logic. `APPLOGGER_BETA_TESTER=true` only activates the mode.

For two apps on the same device, they share the same `device_id` but have different
`device_fingerprint` hashes (fingerprint includes package_name). Use `device_id` or
a shared `user_id` to correlate events across apps.

## OperationTrace — Span de performance

Para medir el tiempo de operaciones críticas y detectar degradación:

```kotlin
// Android — en Application o cualquier clase que tenga AppLogger
val trace = AppLoggerSDK.startTrace("api_call", "endpoint" to url)
try {
    val result = api.fetch(url)
    trace.end(mapOf("status_code" to result.status))
} catch (e: Exception) {
    trace.endWithError(e, failureReason = "network_error")
}
// → Emite métrica trace.api_call con duration_ms
```

Ver skill `applogger-advanced-features` para el API completa de OperationTrace.

## What not to do on first pass

1. Do not replace every existing logger call in the whole codebase.
2. Do not add PII to logs.
3. Do not add AppLogger to unrelated layers without a reason.
4. Do not introduce platform-specific iOS host code if the project is KMP.
5. Do not expose raw `ANDROID_ID` — the SDK already pseudonymizes it via SHA-256.
