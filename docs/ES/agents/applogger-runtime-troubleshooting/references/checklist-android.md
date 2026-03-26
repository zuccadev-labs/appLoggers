# Android Runtime Checklist

## Initialization
1. Verify `Application` initialization path runs.
2. Verify INTERNET and ACCESS_NETWORK_STATE permissions in AndroidManifest.
3. Verify BuildConfig values are sourced from expected keys (`LOGGER_URL`, `LOGGER_KEY`, `LOGGER_DEBUG`).
4. Confirm remote endpoint uses HTTPS.
5. Verify `environment` is set (`production` / `staging` / `development`).

## Basic logging
6. Emit one `info` and one `error` test event.
7. Inspect `AppLoggerHealth.snapshot()` values: `isInitialized=true`, `transportAvailable=true`.
8. Verify events appear in Supabase `app_logs` table.

## Device fingerprint
9. Verify `AppLoggerSDK.getDeviceFingerprint()` returns a 64-char hex string.
10. Verify `extra.device_fingerprint` is present in events in Supabase.
11. Verify CLI `--fingerprint` filter returns matching events.

## Remote config
12. Verify `.remoteConfigEnabled(true)` is in `AppLoggerConfig.Builder()` if remote control is needed.
13. Verify remote config changes (e.g., `minLevel`) are applied within one polling interval.
14. Verify `remoteConfigIntervalSeconds` is between 30 and 3600.

## Beta tester
15. Verify `AppLoggerSDK.setBetaTester(email)` adds `beta_tester_email` to event extras.
16. Verify `AppLoggerSDK.clearBetaTester()` removes `beta_tester_email`.
17. Verify `APPLOGGER_BETA_TESTER` in local.properties is boolean only — email comes from auth flow at runtime.

## Distributed tracing
18. Verify `addGlobalExtra("trace_id", id)` adds `trace_id` to all subsequent events.
19. Verify `removeGlobalExtra("trace_id")` removes `trace_id`.

## Session & identity
20. Verify `newSession()` generates a new `session_id`.
21. Verify `setAnonymousUserId(id)` appears in events.
22. Verify `clearAnonymousUserId()` removes `user_id` from subsequent events.

## Global extras
23. Verify `addGlobalExtra(key, value)` attaches to all subsequent events.
24. Verify `removeGlobalExtra(key)` removes from subsequent events.
25. Verify `clearGlobalExtra()` removes all custom extras.

## OperationTrace
26. Verify `AppLoggerSDK.startTrace("test_op").end()` emits a `trace.test_op` metric in Supabase.
27. Verify `endWithError(e)` emits an ERROR-level event with `tag = "Trace.test_op"`.
28. Verify unclosed spans emit nothing — only `end()` / `endWithError()` triggers emission.

## DataBudget
29. If `dailyDataLimitMb > 0` is configured: verify events arrive normally before limit.
30. Verify ERROR/CRITICAL events arrive even when daily limit is exceeded.

## Lifecycle & shutdown
31. Verify `AppLoggerHealth.snapshot()` is stable after multiple events.
32. Verify events are not lost when the app is killed — `BatchProcessor.shutdown()` flushes pending events.

## Health & diagnostics
33. Verify `health.consecutiveFailures` is 0 after a successful flush.
34. Verify `health.deadLetterCount` is 0 (no permanently failed events).
35. Verify `health.eventsDroppedDueToBufferOverflow` is 0 under normal load.
