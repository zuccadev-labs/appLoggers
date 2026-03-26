# Smoke Tests

1. Verify initialization runs once.
2. Emit one `info`, one `warn`, one `error`, one `metric`.
3. Trigger manual flush.
4. Verify health snapshot before and after flush.
5. Confirm events arrive in backend.
6. Verify canonical imports compile: `com.applogger.core.*` and `com.applogger.transport.supabase.*`.
7. Verify Logcat condition explicitly: visible only with `isDebugMode=true` and `consoleOutput=true`.
8. OperationTrace smoke: call `AppLoggerSDK.startTrace("smoke_test")`, call `.end()`, verify `trace.smoke_test` metric arrives in backend (`apploggers telemetry query --source metrics --name trace.smoke_test`).
9. DataBudget smoke: if `dailyDataLimitMb > 0` is configured, verify events arrive normally before limit and that ERROR events still arrive when limit is exceeded.
10. HMAC batch integrity: run `apploggers verify --from <date> --to <date>` and confirm `ok=true` and `tampered=0` for all batches in range.
