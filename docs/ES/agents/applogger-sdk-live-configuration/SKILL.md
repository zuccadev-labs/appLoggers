---
name: applogger-sdk-live-configuration
description: Configure AppLogger SDK in a real project environment by inspecting local.properties, adding only missing keys, and validating end-to-end initialization.
---

# AppLogger SDK Live Configuration

## When to use this skill

Use this skill when the user asks for practical, in-environment SDK setup completion (not only conceptual guidance).

Examples:

1. "termina de configurar el sdk"
2. "revisa local.properties y ajusta lo faltante"
3. "deja el sdk operativo con validacion"

## Mandatory constraints

1. Read `local.properties` if present.
2. If `local.properties` is missing, create from `local.properties.example` only when requested.
3. Add only missing AppLogger keys.
4. Never rename/delete unrelated existing keys.
5. Never print full secrets in output.

## Required SDK config keys

1. `appLogger.url`
2. `appLogger.anonKey`
3. `appLogger.debug`

## Workflow

1. Detect project target and module wiring.
2. Inspect `local.properties` and `local.properties.example`.
3. Add missing AppLogger keys only.
4. Validate Gradle mapping to BuildConfig.
5. Validate SDK initialization path.
6. Run build/test smoke checks.
7. Report remaining manual inputs (if secrets are unavailable).

## Validation commands

1. `cd sdk && ./gradlew check`
2. `cd sdk && ./gradlew assemble`

## Gaps outside automation

1. Real secret values if the user has not provided them.
2. Supabase dashboard access to obtain URL/anon key.

## References bundled with this skill

1. `references/local-properties-live-checklist.md`

## Output standard

1. Explicit list of keys found/missing/added.
2. Exact files touched.
3. Validation status and blockers.
4. Safe next steps for unresolved secrets.
