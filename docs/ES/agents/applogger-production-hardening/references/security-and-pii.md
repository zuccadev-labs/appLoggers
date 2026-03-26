# Security and PII Rules

1. Never log PII directly (names, phone numbers, addresses, IMEI).
2. Never log access tokens, API keys, or secrets.
3. Keep `debugMode=false` for non-local builds.
4. Use HTTPS endpoints in production.
5. Device fingerprint is pseudonymized via SHA-256 — not raw PII.
6. Beta tester email is opt-in only (requires `APPLOGGER_BETA_TESTER=true` + explicit developer call).
7. Beta tester data can be erased via `apploggers erase --user-id <email>` (GDPR Art. 17).
8. Beta tester mappings auto-expire after 90 days (GDPR Art. 5.1.e — data minimization).
9. Consent levels control what data is attached — use `setConsent(ConsentLevel)` (not boolean): `STRICT` (errors only, anonymized), `PERFORMANCE` (+ metrics), `MARKETING` (full telemetry, requires opt-in).
10. Data minimization in `STRICT` mode (when `dataMinimizationEnabled=true`, default): suppresses `user_id` → null, pseudonymizes `device_id` → SHA-256.
11. Initialize with `defaultConsentLevel(ConsentLevel.STRICT)` for apps that require consent before collecting data. Upgrade to `MARKETING` only after explicit user opt-in.

`local.properties` rule:

1. Add missing AppLogger keys only.
2. Do not alter unrelated existing keys.

## Beta tester keys (optional, beta builds only)

| Key | Type | Purpose |
|---|---|---|
| `APPLOGGER_BETA_TESTER` | boolean | Activates beta tester mode |

The tester's email is NOT a config key — it comes from the developer's auth flow at runtime.
