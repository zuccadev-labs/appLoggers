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
11. Verify `setConsent(ConsentLevel.MARKETING)` persists in `NSUserDefaults`.
12. Verify events are NOT sent (INFO/DEBUG) when consent is `ConsentLevel.STRICT`.
13. Verify ERROR/CRITICAL events still arrive in `ConsentLevel.STRICT` mode.
14. Verify `getConsent()` returns the persisted level after app restart.

## Remote config
15. Verify `startRemoteConfig(intervalSeconds)` starts polling.
16. Verify remote config changes (e.g., `minLevel`) are applied within one polling interval.
17. Verify `stopRemoteConfig()` stops polling.
18. Verify interval is between 30 and 3600 seconds.

## Beta tester
19. Verify `setBetaTester(email)` adds `beta_tester_email` to event extras.
20. Verify `clearBetaTester()` removes `beta_tester_email`.

## Distributed tracing
21. Verify `setTraceId(id)` adds `trace_id` to all subsequent events.
22. Verify `clearTraceId()` removes `trace_id`.
23. Verify events from two devices with the same `trace_id` can be correlated in CLI.

## Breadcrumbs
24. Verify `recordBreadcrumb(action, screen?, metadata?)` accumulates in `extra.breadcrumbs`.
25. Verify breadcrumbs are included in error events.

## Scoped logger
26. Verify `withTag("TAG")` produces events with the correct fixed tag.
27. Verify scoped logger supports all log levels (`i/w/e/c/metric`).
28. Verify `newScope("key" to value)` pre-injects attributes in all events from the scope.
29. Verify `childScope("key" to value)` inherits parent scope attributes.

## Session & variant
30. Verify `newSession()` generates a new `session_id`.
31. Verify `setSessionVariant("variant_name")` appears as `variant` field in event data.
32. Verify `setSessionVariant(null)` clears variant from subsequent events.

## Background & lifecycle
33. Verify `flush()` is called when app enters background.
34. Verify no crash or hang occurs during flush.

## System snapshot
35. Verify `NSProcessInfo.thermalState` is captured in device info.
36. Verify `lowPowerModeEnabled` is captured in device info.
