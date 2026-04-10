# Android Integration Patterns

## Good places to initialize

1. `Application`
2. DI bootstrap (`Hilt`, `Koin`, manual service locator)
3. Startup component that already wires analytics or crash reporting
4. Dedicated initializer object invoked by `Application` when the app has multiple services, receivers, workers, or embedded servers

## Good first logging points

1. App startup completed
2. Authentication failure
3. Network anomaly or timeout
4. Critical purchase or playback failure
5. Foreground service boot/shutdown when the app is headless or TV-oriented
6. Embedded API/gRPC/WebSocket server startup latency if the app exposes local services

## Health verification

Use `AppLoggerHealth.snapshot()` after initialization and after one emitted event.

For service-oriented apps, also verify a final health snapshot plus `AppLoggerSDK.flush()` on controlled shutdown.

## Device fingerprint

The SDK auto-generates a pseudonymized device fingerprint on initialization:
`SHA-256(ANDROID_ID + ":" + package_name)`. No developer action is required.

- Survives app reinstalls. Resets only on factory reset.
- Stored in `extra->>'device_fingerprint'` on every event in `app_logs`.
- Used as key in `device_remote_config` table for per-device remote config.
- Read it via `AppLoggerSDK.getDeviceFingerprint()` after initialization.

## Remote config integration

To enable remote debug control per device:

```kotlin
AppLoggerConfig.Builder()
    .remoteConfigEnabled(true)
    .remoteConfigIntervalSeconds(300)
    // ...
    .build()
```

The SDK polls `device_remote_config` table and applies overrides (minLevel, debug, tags, sampling).
ERROR and CRITICAL events always pass — they are never filtered by remote config.

Manage rules via CLI: `apploggers remote-config set|list|delete`.

## Android forensic pattern checklist

1. `Application.onCreate()` initializes AppLogger before starting foreground services.
2. A dedicated `LogTags` object or wrapper centralizes domain tags.
3. The app starts with a pseudonymous device identifier and may later switch to authenticated user correlation.
4. `app_package` is injected globally once, not repeated manually in every call.
5. Long-lived services log uptime and call `flush()` during shutdown.
6. Metrics include stable dimensions like `reason`, `action`, `status`, or `platform`.
7. Warning-level anomalies use structured fields such as `anomalyType` or explicit `extra` keys instead of only prose.
8. Production messages avoid PII and avoid unstable decorative formatting.

## Common review questions

1. Is there already a wrapper around logs?
2. Should AppLogger be called directly or behind an app-specific facade?
3. Where are secrets loaded today?
4. Does the app need per-device remote debug control? If yes, enable `remoteConfigEnabled(true)`.
5. Is the app duplicating AppLogger secrets in packaged assets plus `BuildConfig`?
6. Does auth success set SDK identity without also printing the user identifier in the message body?
