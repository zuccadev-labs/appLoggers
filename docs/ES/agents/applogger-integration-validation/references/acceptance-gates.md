# Acceptance Gates

All gates must pass:

1. SDK initializes correctly.
2. Events are delivered.
3. No forbidden sensitive data in logs.
4. Health snapshot does not indicate persistent degradation.
5. local.properties policy is respected (only missing keys added; unrelated keys untouched).
6. `device_id` top-level column in `app_logs`/`app_metrics` is a non-empty UUID v5 — never empty string. If empty, verify migrations 001/002 were applied.
6b. `extra->>'device_fingerprint'` is SHA-256 pseudonymized (not raw `ANDROID_ID`). Allowed to be empty string `""` on emulators or fresh factory-reset devices — `device_id` is NOT affected.
7. Remote config polling works: CLI `remote-config set --debug true` → SDK picks up within polling interval → debug logs appear.
8. Remote config deactivation works: CLI `remote-config set --debug false` → SDK stops debug logging within polling interval.
9. Beta tester flow (when `APPLOGGER_BETA_TESTER=true`): `setBetaTester(email)` attaches `is_beta_tester` and `beta_tester_email` to events.
10. Two-app correlation: frontend sends email → Supabase trigger stores mapping → backend on same device auto-fills email via `trg_correlate_beta_tester`.
11. `app_package` global extra is present in all events (distinguishes apps on same device).
12. `environment` field in `app_logs` and `app_metrics` is non-null and matches expected value (`production`, `staging`, `development`). If null: migrations 007/008 not applied — run via MCP before re-testing.
13. HMAC batch integrity — **solo si** `.integritySecret()` está configurado: `log_batches` recibe filas y el comando `verify` retorna `ok=true` y `tampered=0`. Sin `integritySecret`, `log_batches` estará vacío — eso es comportamiento correcto, no un error.
14. GDPR cascade — `erase --confirm` borra de las 5 tablas (app_logs, app_metrics, beta_tester_devices, device_remote_config, log_batches).
15. OperationTrace — `startTrace`/`end` emite metric `trace.*` con `duration_ms`.
16. DataBudget — `recordBytesSent` respeta límite diario, `shouldShedLowPriority` activa cuando se excede.
