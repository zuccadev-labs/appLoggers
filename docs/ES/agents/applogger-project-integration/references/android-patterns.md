# Android Integration Patterns

## Good places to initialize

1. `Application`
2. DI bootstrap (`Hilt`, `Koin`, manual service locator)
3. Startup component that already wires analytics or crash reporting

## Good first logging points

1. App startup completed
2. Authentication failure
3. Network anomaly or timeout
4. Critical purchase or playback failure

## Health verification

Use `AppLoggerHealth.snapshot()` after initialization and after one emitted event.

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

## Common review questions

1. Is there already a wrapper around logs?
2. Should AppLogger be called directly or behind an app-specific facade?
3. Where are secrets loaded today?
4. Does the app need per-device remote debug control? If yes, enable `remoteConfigEnabled(true)`.
