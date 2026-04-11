---
name: applogger-production-hardening
description: Harden AppLogger configuration for production readiness. Use when the user asks for security, privacy, stable runtime tuning, and release-safe telemetry defaults.
---

# AppLogger Production Hardening

## When to use this skill

Use this skill when the integration already works and needs production quality:

1. Security and privacy checks.
2. Runtime tuning for stability.
3. Release-safe defaults.

## Mandatory constraints

1. Never allow sensitive values to be committed.
2. Verify `local.properties` keys first and add only missing AppLogger keys.
3. Do not modify unrelated `local.properties` variables.
4. Keep recommendations evidence-based.
5. Always recommend setting `environment` to distinguish production from staging.
6. Always recommend `minLevel(LogMinLevel.INFO)` or higher in production to discard DEBUG events before the pipeline.
7. Always recommend `config.validate()` during development to catch config issues early.
8. Never recommend `isDebugMode=true` with `environment="production"` — `validate()` flags this as an error.
9. Device fingerprint is pseudonymized via SHA-256 — never expose raw `ANDROID_ID` in logs.
10. Remote config: verify `remoteConfigIntervalSeconds` is between 30–3600. Default polling disabled unless endpoint configured.
11. Beta tester mode: `APPLOGGER_BETA_TESTER=true` is boolean only. Email captured at runtime from developer's auth flow — never hardcode emails in config.
12. Beta tester data auto-expires after 90 days (GDPR Art. 5.1.e). Verify `expire-beta-tester-mappings-weekly` is scheduled in production and points to `apploggers.expire_beta_tester_mappings()`.
13. DataBudgetManager: for bandwidth-sensitive apps, set `dailyDataLimitMb(n)` in `AppLoggerConfig.Builder()`. Default is `0` (disabled). On WiFi, effective limit doubles automatically (wifiMultiplier = 2×). ERROR and CRITICAL events are never shed regardless of budget state.
14. Avoid duplicating AppLogger URL/key in packaged asset files if the same values are already mapped via secure build configuration.
15. For headless/service apps, review shutdown paths and ensure there is an intentional `flush()` before process teardown.
16. For constrained devices such as TVs, kiosks, or low-storage appliances, choose `offlinePersistenceMode` deliberately instead of keeping the default implicitly.
17. Keep production log messages searchable and low-noise; avoid PII, raw tokens, and decorative message formats.

## Workflow

1. Review current configuration and environment loading.
2. Apply security and PII guardrails.
3. Tune batch, flush, and overflow settings.
4. Validate release defaults.
5. Produce hardening checklist.

## Hardening focus for mature Android service integrations

1. Ensure `minLevel(LogMinLevel.INFO)` or stricter in production.
2. Use `config.validate()` in development builds so bad prod/debug combinations are caught before release.
3. Tune buffer overflow and flush interval for the process lifetime profile: aggressive flush for headless services, slower flush for interactive apps.
4. If storage is constrained, justify `OfflinePersistenceMode.NONE`; otherwise prefer an explicit durable mode.
5. Review uncaught-exception handlers so AppLogger logging and upstream crash handling are both preserved.

## References bundled with this skill

1. `references/security-and-pii.md`
2. `references/runtime-tuning.md`
3. `references/release-checklist.md`

## Output standard

1. Separate required hardening from optional tuning.
2. Provide concrete parameter values.
3. End with release-ready checklist.
