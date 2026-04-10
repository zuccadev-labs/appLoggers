---
name: applogger-existing-integration-audit
description: Audita una integración existente de AppLogger en un proyecto Kotlin o Android, detecta gaps de operación y deja una ruta segura de corrección o upgrade sin romper el comportamiento actual.
---

# AppLogger Existing Integration Audit

## When to use this skill

Use this skill when the user wants the agent to:

1. Read a project that already uses AppLogger.
2. Determine whether the current integration is correct, outdated, noisy, or risky.
3. Propose or implement safe corrections without rebuilding the integration from zero.
4. Align the project with the current AppLoggers telemetry contract and operational model.

Do not use this skill when:

1. The project does not use AppLogger yet.
2. The user wants only a clean first-time installation.
3. The task is limited to backend schema setup.

## Mandatory constraints

1. Perform a forensic audit before editing anything.
2. Separate proven good patterns from recommended corrections.
3. Preserve behavior unless the user explicitly asks for refactors beyond AppLogger.
4. Resolve the correct target AppLogger version before proposing upgrades.
5. Check whether all AppLogger artifacts are on the same version.
6. Verify whether initialization is centralized or fragmented.
7. Verify whether tags are centralized or ad-hoc.
8. Verify whether identity handling starts pseudonymously and changes later through auth.
9. Verify whether logs contain PII, tokens, raw device identifiers, or unstable message formats.
10. Verify whether health snapshots and `flush()` exist in lifecycle-critical shutdown paths.
11. Verify whether credentials are duplicated across `BuildConfig`, assets, env files, or other config layers.
12. Treat `app_package`, `source_scope`, `source_file`, and `source_method` as the current additive telemetry contract.

## Audit workflow

1. Locate AppLogger dependencies and versions.
2. Locate initialization entry points.
3. Inspect config-source mapping (`local.properties`, `BuildConfig`, env files, assets).
4. Inspect tag taxonomy and logging facade patterns.
5. Inspect representative call sites across startup, auth, network/server, and shutdown.
6. Inspect metrics usage and structured dimensions.
7. Classify findings into:
   - correct and keep
   - correct but needs documentation
   - should improve soon
   - must fix before relying on the telemetry operationally
8. Apply minimal safe corrections when requested.
9. Validate with smoke checks or static acceptance gates.

## Evidence model

Report findings using this structure:

1. Initialization
2. Identity and privacy
3. Tags and instrumentation model
4. Runtime stability and flush behavior
5. Version alignment
6. Config hygiene

## Recommended forensic baseline

1. Dedicated initializer object invoked from `Application.onCreate()`.
2. `SupabaseTransport` created explicitly.
3. `AppLoggerConfig.Builder()` validated in development.
4. `app_package` injected globally.
5. Startup BOOT log plus initial health snapshot.
6. Domain tags centralized in one object.
7. Auth flow updates SDK identity without printing that identifier in message text.
8. Shutdown path emits final health and calls `AppLoggerSDK.flush()`.

## References bundled with this skill

1. `references/audit-checklist.md`
2. `references/klinema-patterns.md`

## Output standard

1. Findings first, ordered by severity.
2. Each finding must say whether it is a bug, an operational risk, a privacy issue, or a maintainability issue.
3. Separate "keep as-is" patterns from "change this" guidance.
4. End with a concrete upgrade path and validation plan.