# iOS KMP Runtime Checklist

1. Verify shared Kotlin initializer executes at startup.
2. Verify AppLogger keys are loaded correctly.
3. Emit one `info` and one `error` event.
4. Inspect `AppLoggerHealth.snapshot()` values.
5. Confirm remote endpoint uses HTTPS.