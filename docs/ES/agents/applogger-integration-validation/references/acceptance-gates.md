# Acceptance Gates

All gates must pass:

1. SDK initializes correctly.
2. Events are delivered.
3. No forbidden sensitive data in logs.
4. Health snapshot does not indicate persistent degradation.
5. local.properties policy is respected (only missing keys added; unrelated keys untouched).
6. Device fingerprint is present and SHA-256 pseudonymized (not raw `ANDROID_ID`).
7. Remote config polling works: CLI `remote-config set --debug true` → SDK picks up within polling interval → debug logs appear.
8. Remote config deactivation works: CLI `remote-config set --debug false` → SDK stops debug logging within polling interval.
9. Beta tester flow (when `APPLOGGER_BETA_TESTER=true`): `setBetaTester(email)` attaches `is_beta_tester` and `beta_tester_email` to events.
10. Two-app correlation: frontend sends email → Supabase trigger stores mapping → backend on same device auto-fills email via `trg_correlate_beta_tester`.
11. `app_package` global extra is present in all events (distinguishes apps on same device).
12. `environment` field is set and matches expected value (`production`, `staging`, `development`).
