# iOS KMP Runtime Checklist

## Initialization
1. Verify shared Kotlin initializer executes at startup.
2. Verify AppLogger keys are loaded correctly.
3. Confirm remote endpoint uses HTTPS.
4. Verify `environment` is set (`production` / `staging` / `development`).

## Basic logging
5. Emit one `info` and one `error` event.
6. Inspect `AppLoggerHealth.snapshot()` values: `isInitialized=true`, `transportAvailable=true`.
7. Verify events appear in Supabase `app_logs` table.

## Device fingerprint
8. Verify `getDeviceFingerprint()` returns a 64-char hex string.
9. Verify `extra.device_fingerprint` is present in events in Supabase.
10. Verify CLI `--fingerprint` filter returns matching events.

## Consent
11. Verify `setConsent(true)` persists in `NSUserDefaults`.
12. Verify events are NOT sent when consent is `false`.
13. Verify events resume after `setConsent(true)`.

## Remote config
14. Verify `startRemoteConfig(intervalSeconds)` starts polling.
15. Verify remote config changes (e.g., `minLevel`) are applied within one polling interval.
16. Verify `stopRemoteConfig()` stops polling.
17. Verify interval is between 30 and 3600 seconds.

## Beta tester
18. Verify `setBetaTester(email)` adds `beta_tester_email` to event extras.
19. Verify `clearBetaTester()` removes `beta_tester_email`.

## Distributed tracing
20. Verify `setTraceId(id)` adds `trace_id` to all subsequent events.
21. Verify `clearTraceId()` removes `trace_id`.
22. Verify events from two devices with the same `trace_id` can be correlated in CLI.

## Breadcrumbs
23. Verify `recordBreadcrumb(label)` accumulates in `extra.breadcrumbs`.
24. Verify breadcrumbs are included in error events.

## Scoped logger
25. Verify `scopedLogger(tag)` produces events with the correct fixed tag.
26. Verify scoped logger supports all log levels (`d/i/w/e/c/metric`).

## Session & variant
27. Verify `newSession()` generates a new `session_id`.
28. Verify `setVariant(name)` appears in event data.
29. Verify `clearVariant()` removes variant from subsequent events.

## Background & lifecycle
30. Verify `flush()` is called when app enters background.
31. Verify no crash or hang occurs during flush.

## System snapshot
32. Verify `NSProcessInfo.thermalState` is captured in device info.
33. Verify `lowPowerModeEnabled` is captured in device info.
