# Common Failures

1. Wrong endpoint format.
2. Invalid API key.
3. Non-HTTPS production endpoint.
4. Missing network permissions on Android.
5. Initialization never called.
6. AppLogger keys missing in `local.properties`.
7. Wrong SDK imports (`com.applogger.sdk.*` instead of `com.applogger.core.*`).
8. Misreading Logcat behavior: expecting console output with `isDebugMode=false`.
9. Passing `throwable` as 3rd positional argument to `warn()` when `anomalyType` is also intended — pass by name: `warn(tag, message, throwable = e, anomalyType = "TYPE")`.
10. Android compile failure due to missing placeholders: `BuildConfig.LOGGER_URL`, `BuildConfig.LOGGER_KEY`, `BuildConfig.LOGGER_DEBUG`.
11. iOS/KMP compile failure caused by using Android entrypoint `AppLoggerSDK` instead of `AppLoggerIos.shared`.
12. Events lost after process termination — `BatchProcessor.shutdown()` must be called (or the lifecycle integration must trigger it) to flush pending events before the process exits. If events disappear after kill, verify the shutdown/flush path is wired to the app lifecycle.
13. OperationTrace span never emits — forgot to call `end()` or `endWithError()`. Spans emit only on explicit close — they do not auto-close.
14. `dailyDataLimitMb` budget exceeded silently — non-critical events stop arriving mid-day with no error. Check if volume drops abruptly around the same time each day; increase the limit or disable it (`dailyDataLimitMb = 0`) if unintended.
15. `remote-config delete` fails silently — verify the `id` UUID matches an existing row in `device_remote_config`. Use `apploggers remote-config list` first to confirm.

16. `setConsent(true/false)` compile error — the API takes `ConsentLevel` enum, not Boolean: `setConsent(ConsentLevel.MARKETING)` / `setConsent(ConsentLevel.STRICT)`.
17. `setVariant()` / `clearVariant()` not found — the real method is `setSessionVariant(variant: String?)`. Pass `null` to clear.
18. `AppLoggerExceptionHandler` unresolved import — it's not an importable class. It's a property: `AppLoggerSDK.exceptionHandler` or `AppLoggerIos.shared.exceptionHandler`.
19. `scopedLogger(tag)` not found — use `withTag(tag)` extension function to get a `TaggedLogger`. For attribute-injecting scope, use `newScope("key" to value)`.

Fix policy for `local.properties`:

1. Add only missing keys.
2. Preserve unrelated keys exactly as they are.

Logcat rule to verify during debugging:

1. Output is shown only when `isDebugMode=true` and `consoleOutput=true`.
2. No additional Android logger wrapper is required for AppLogger console output.
