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

20. `environment` = NULL en Supabase pese a que el SDK envía el valor — la columna no existe porque la migración no fue aplicada. `app_logs.environment` requiere migration 007; `app_metrics.environment` requiere migration 008. Aplicar con MCP `mcp_supabase_execute_sql` o desde el SQL editor de Supabase.

21. `device_id` aparece vacío en Supabase (`app_logs`) — **es diferente a `device_fingerprint`**. El `device_id` top-level es un UUID v5 generado por el SDK desde los metadatos del dispositivo; **nunca es vacío**. Si la columna muestra vacío, verificar que migration 001 fue aplicada y que el SDK estaba inicializado al emitir el primer evento. Si el problema es `extra->>'device_fingerprint'` vacío (string `""`), eso es normal en emuladores donde `ANDROID_ID = null` — no afecta `device_id`.

22. `log_batches` siempre vacío — requiere `.integritySecret(secret)` en el builder. Por defecto está desactivado (`integritySecret = ""`). Sin esta clave, el SDK nunca escribe en `log_batches`. Generar el secreto con un CSPRNG real, por ejemplo `openssl rand -hex 32`, PowerShell o el gestor de secretos del pipeline.

23. `environment` de un batch en `log_batches` es NULL — `SupabaseTransport.storeBatchManifest` omite `environment` si está en blanco. Como `AppLoggerConfig.Builder.environment()` tiene default `"production"` y rechaza strings vacíos (`ifBlank { "production" }`), esto solo ocurre si migration 011 no fue aplicada (la columna no existe) o si se pasó explícitamente un environment inválido.

Fix policy for `local.properties`:

1. Add only missing keys.
2. Preserve unrelated keys exactly as they are.

Logcat rule to verify during debugging:

1. Output is shown only when `isDebugMode=true` and `consoleOutput=true`.
2. No additional Android logger wrapper is required for AppLogger console output.
