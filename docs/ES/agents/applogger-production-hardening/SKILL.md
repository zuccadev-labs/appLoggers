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

## Workflow

1. Review current configuration and environment loading.
2. Apply security and PII guardrails.
3. Tune batch, flush, and overflow settings.
4. Validate release defaults.
5. Produce hardening checklist.

## References bundled with this skill

1. `references/security-and-pii.md`
2. `references/runtime-tuning.md`
3. `references/release-checklist.md`

## Output standard

1. Separate required hardening from optional tuning.
2. Provide concrete parameter values.
3. End with release-ready checklist.
