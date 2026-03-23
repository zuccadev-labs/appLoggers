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

## Configuration model — two options

### Option A: environment variables only (simple / CI)

Export these variables before running any CLI command:

```bash
# Linux / macOS
export appLogger_supabaseUrl="https://YOUR_PROJECT.supabase.co"
export appLogger_supabaseKey="YOUR_SERVICE_ROLE_KEY"

# Windows PowerShell
$env:appLogger_supabaseUrl = "https://YOUR_PROJECT.supabase.co"
$env:appLogger_supabaseKey = "YOUR_SERVICE_ROLE_KEY"
```

Aliases also accepted: `APPLOGGER_SUPABASE_URL` / `APPLOGGER_SUPABASE_KEY`.

### Option B: project config file (recommended for multi-project / persistent)

File location: `~/.apploggers/cli.json` (created automatically on first run with a template).

```json
{
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "display_name": "My Application",
      "workspace_roots": [
        "/path/to/workspace"
      ],
      "supabase": {
        "url": "https://YOUR_PROJECT.supabase.co",
        "api_key_env": "APPLOGGER_SUPABASE_KEY"
      }
    }
  ]
}
```

**CRITICAL — `api_key_env` field:**
- `api_key_env` must contain the **NAME** of the environment variable, NOT the key value itself.
- The CLI calls `os.Getenv(api_key_env)` at runtime to read the actual key.
- If you put the JWT value directly in `api_key_env`, the CLI will fail with: `project "X" requires secret env eyJhbGci...`

```json
// ❌ WRONG — JWT value in api_key_env
"api_key_env": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

// ✅ CORRECT — variable name in api_key_env
"api_key_env": "APPLOGGER_SUPABASE_KEY"
```

Then export the actual key separately:

```bash
export APPLOGGER_SUPABASE_KEY="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**Optional fields** (omit to use defaults):
- `schema`: PostgreSQL schema. Default: `public`. Only set if tables were migrated to a custom schema.
- `logs_table`: Logs table name. Default: `app_logs`. Only set if a different name was used in migrations.
- `metrics_table`: Metrics table name. Default: `app_metrics`. Only set if a different name was used in migrations.
- `timeout_seconds`: HTTP timeout (1-120). Default: `15`.

## Required CLI env

1. `appLogger_supabaseUrl` (or `APPLOGGER_SUPABASE_URL`)
2. `appLogger_supabaseKey` (or `APPLOGGER_SUPABASE_KEY`) — must be service_role key

## Optional CLI env

1. `appLogger_supabaseSchema` / `APPLOGGER_SUPABASE_SCHEMA`
2. `appLogger_supabaseLogTable` / `APPLOGGER_SUPABASE_LOG_TABLE`
3. `appLogger_supabaseMetricTable` / `APPLOGGER_SUPABASE_METRIC_TABLE`
4. `appLogger_supabaseTimeoutSeconds` / `APPLOGGER_SUPABASE_TIMEOUT_SECONDS`
5. `APPLOGGER_CONFIG` — override path to cli.json
6. `APPLOGGER_PROJECT` — explicit project name selection

## Workflow

1. Install or verify CLI binary (`apploggers version`).
2. Choose configuration option (env vars or cli.json).
3. If using cli.json: verify `api_key_env` contains a variable NAME, not a key value.
4. Export the actual service_role key in the current shell.
5. Run readiness commands:
   - `apploggers health --output json`
   - `apploggers telemetry query --source logs --limit 5 --output json`
6. Validate metrics query:
   - `apploggers telemetry query --source metrics --limit 5 --output json`

## Diagnosing configuration errors

| Error message | Cause | Fix |
|---|---|---|
| `requires secret env eyJhbGci...` | JWT value placed in `api_key_env` instead of variable name | Set `api_key_env` to the variable name (e.g. `"APPLOGGER_SUPABASE_KEY"`) and export the key separately |
| `missing Supabase URL` | No URL configured | Set `appLogger_supabaseUrl` or add `supabase.url` to cli.json |
| `missing Supabase API key` | No key configured | Set `appLogger_supabaseKey` or add `api_key_env` to cli.json and export the variable |
| `project "X" not found` | `--project` or `APPLOGGER_PROJECT` references a name not in cli.json | Check `name` fields in cli.json match exactly |

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
