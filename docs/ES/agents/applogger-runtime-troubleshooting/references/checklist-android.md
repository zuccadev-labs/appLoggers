# Android Runtime Checklist

1. Verify `Application` initialization path runs.
2. Verify INTERNET and ACCESS_NETWORK_STATE permissions.
3. Verify BuildConfig values are sourced from expected keys.
4. Emit one `info` and one `error` test event.
5. Inspect `AppLoggerHealth.snapshot()` before and after flush.