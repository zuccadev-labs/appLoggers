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

Fix policy for `local.properties`:

1. Add only missing keys.
2. Preserve unrelated keys exactly as they are.

Logcat rule to verify during debugging:

1. Output is shown only when `isDebugMode=true` and `consoleOutput=true`.
2. No additional Android logger wrapper is required for AppLogger console output.