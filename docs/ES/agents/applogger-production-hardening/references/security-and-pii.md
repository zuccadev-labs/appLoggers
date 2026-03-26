# Security and PII Rules

1. Never log PII directly (names, phone numbers, addresses, IMEI).
2. Never log access tokens, API keys, or secrets.
3. Keep `debugMode=false` for non-local builds.
4. Use HTTPS endpoints in production.
5. Device fingerprint is pseudonymized via SHA-256 — not raw PII.
6. Beta tester email is opt-in only (requires `APPLOGGER_BETA_TESTER=true` + explicit developer call).
7. Beta tester data can be erased via `apploggers erase --user-id <email>` (GDPR Art. 17).
8. Beta tester mappings auto-expire after 90 days (GDPR Art. 5.1.e — data minimization).
9. Consent levels (`STRICT`, `MARKETING`) control what data is attached — respect user choice.
10. Data minimization mode (STRICT consent) suppresses `user_id` and pseudonymizes `device_id`.

`local.properties` rule:

1. Add missing AppLogger keys only.
2. Do not alter unrelated existing keys.

## Beta tester keys (optional, beta builds only)

| Key | Type | Purpose |
|---|---|---|
| `APPLOGGER_BETA_TESTER` | boolean | Activates beta tester mode |

The tester's email is NOT a config key — it comes from the developer's auth flow at runtime.
