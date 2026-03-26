# Release Checklist

1. Endpoint is HTTPS.
2. Keys load from non-committed source.
3. `debugMode=false` in release.
4. One startup event arrives in backend.
5. Health snapshot stable after smoke flow.
6. Device fingerprint is SHA-256 pseudonymized — no raw `ANDROID_ID` in logs or extras.
7. Remote config polling disabled or set to production interval (≥300s). No `--debug true` left active globally.
8. `APPLOGGER_BETA_TESTER=false` (or absent) in production release builds unless intentionally testing.
9. Beta tester email is NOT hardcoded — comes from developer's auth flow at runtime.
10. `expire_beta_tester_mappings()` is scheduled via pg_cron (GDPR Art. 5.1.e — 90-day TTL).
11. Consent level (`STRICT` / `MARKETING`) configured correctly — `STRICT` suppresses `user_id` and pseudonymizes `device_id`.
12. `environment("production")` is set explicitly in `AppLoggerConfig.Builder()`.
