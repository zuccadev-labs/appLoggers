---
name: applogger-runtime-troubleshooting
description: Diagnose runtime telemetry issues after AppLogger is installed. Use when the user says things like "no llegan eventos", "transportAvailable false", "buffer no baja", "debug AppLogger", or "why logs are not sent".
---

# AppLogger Runtime Troubleshooting

## When to use this skill

Use this skill when AppLogger is already integrated but behavior is wrong:

1. Events are not delivered.
2. Health snapshot indicates degraded state.
3. Buffer grows and does not drain.
4. Transport errors keep happening.

Do not use this skill when:

1. The SDK is not installed yet.
2. The user asks for full initial setup from zero.

## Mandatory constraints

1. Collect evidence before proposing fixes.
2. Prioritize smallest reversible fix first.
3. Verify `local.properties` keys and add only missing AppLogger keys if needed.
4. Never modify unrelated existing variables in `local.properties`.

## Workflow

1. Confirm current SDK initialization path.
2. Inspect endpoint, API key, and HTTPS usage.
3. Inspect `local.properties` keys and append only missing AppLogger keys.
4. Run health diagnostics.
5. Trace emission path and transport path.
6. Rank likely root causes.
7. Apply fix and revalidate.

## References bundled with this skill

1. `references/health-diagnostics.md`
2. `references/common-failures.md`
3. `references/checklist-android.md`
4. `references/checklist-ios-kmp.md`

## Output standard

1. Show evidence for each suspected cause.
2. Give exact remediation steps.
3. End with a revalidation checklist.