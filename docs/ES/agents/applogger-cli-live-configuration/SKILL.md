---
name: applogger-cli-live-configuration
description: Perform live operational configuration of AppLogger CLI including install, environment exports, service_role key model, and runtime validation.
---

# AppLogger CLI Live Configuration

## When to use this skill

Use this skill when the user needs live CLI setup in an environment (local machine, server, CI runner), including operational readiness checks.

Examples:

1. "configura el cli en vivo"
2. "instala el cli y exporta variables"
3. "deja monitoreo operativo listo"

## Mandatory constraints

1. Enforce service_role key for CLI reads.
2. Never recommend anon/publishable key for CLI operations.
3. Keep secrets out of logs and commits.
4. Validate command execution after configuration.

## Required CLI env

1. `APPLOGGER_SUPABASE_URL`
2. `APPLOGGER_SUPABASE_KEY` (service_role)

## Optional CLI env

1. `APPLOGGER_SUPABASE_SCHEMA`
2. `APPLOGGER_SUPABASE_LOG_TABLE`
3. `APPLOGGER_SUPABASE_METRIC_TABLE`
4. `APPLOGGER_SUPABASE_TIMEOUT_SECONDS`

## Workflow

1. Install or verify CLI binary.
2. Export required environment variables for current shell.
3. Configure persistent env in OS/CI secret store.
4. Run readiness commands:
   - `applogger-cli health --output json`
   - `applogger-cli telemetry query --source logs --limit 5 --output json`
5. Validate metrics query with name filter:
   - `applogger-cli telemetry query --source metrics --name <metric> --limit 5 --output json`

## Gaps outside automation

1. Obtaining service_role key from secure source.
2. Infra-level service account creation.
3. CI secret management policy approval.

## References bundled with this skill

1. `references/cli-live-setup-runbook.md`

## Output standard

1. Show what was configured now vs persistent configuration pending.
2. Show validation command results summary.
3. Report blockers by environment (local/server/CI).
