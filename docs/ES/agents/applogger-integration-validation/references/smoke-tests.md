# Smoke Tests

1. Verify initialization runs once.
2. Emit one `info`, one `warn`, one `error`, one `metric`.
3. Trigger manual flush.
4. Verify health snapshot before and after flush.
5. Confirm events arrive in backend.
6. Verify canonical imports compile: `com.applogger.core.*` and `com.applogger.transport.supabase.*`.
7. Verify Logcat condition explicitly: visible only with `isDebugMode=true` and `consoleOutput=true`.
