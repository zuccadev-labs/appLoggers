---
name: applogger-cli-live-configuration
description: Perform live operational configuration of AppLogger CLI — install, configure cli.json, validate service_role key model, and confirm runtime readiness.
---

# AppLogger CLI Live Configuration

## When to use this skill

Use this skill when the user needs live CLI setup in an environment (local machine, server, proceso SSE/Go), including operational readiness checks.

Examples:

1. "configura el cli en vivo"
2. "deja el cli listo para usar"
3. "deja monitoreo operativo listo"
4. "configura el cli para el agente"

## Mandatory constraints

1. Enforce `service_role key` for CLI reads — never `anon/publishable key`.
2. Keep secrets out of logs and commits.
3. Always configure via `~/.apploggers/cli.json` — never instruir al usuario a exportar variables de entorno para URL o key.
4. Validate command execution after configuration.

---

## Configuration model — single source of truth

The CLI creates `~/.apploggers/cli.json` automatically on first run. This file is the only configuration source for local use, AI agents, and SSE processes.

**There is nothing else to configure.** Edit that file and run the CLI.

```
Windows : C:\Users\<usuario>\.apploggers\cli.json
Linux   : /home/<usuario>/.apploggers/cli.json
macOS   : /Users/<usuario>/.apploggers/cli.json
```

Config resolution order (source: `loadSupabaseConfig()` in `supabase.go`):

1. `~/.apploggers/cli.json` — **always wins if the file exists**.
2. Direct environment variables — only read if `~/.apploggers/cli.json` does NOT exist (legacy compatibility, deprecated for local use).

---

## Configuring `cli.json`

### Standard configuration — `api_key` direct value

```json
{
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "display_name": "My Application",
      "workspace_roots": ["/path/to/workspace"],
      "supabase": {
        "url": "https://YOUR_PROJECT.supabase.co",
        "api_key": "eyJhbGci..."
      }
    }
  ]
}
```

The key lives in the file. The file is not versioned. No env vars to export. Works for all use cases: local developer, AI agent, SSE process, MCP.

### Alternative — `api_key_env` (when the key must not be stored in the file)

`api_key_env` must contain the **NAME** of the environment variable (UPPERCASE), not the key value. The URL always goes in the json — there is no environment variable for the URL in this path.

```json
"supabase": {
  "url": "https://YOUR_PROJECT.supabase.co",
  "api_key_env": "APPLOGGER_SUPABASE_KEY"
}
```

Only the key is exported as an environment variable:

```bash
# Linux / macOS
export APPLOGGER_SUPABASE_KEY="eyJhbGci..."

# Windows PowerShell
$env:APPLOGGER_SUPABASE_KEY = "eyJhbGci..."
```

The CLI calls `os.Getenv("APPLOGGER_SUPABASE_KEY")` at runtime. If the variable is empty or not exported, the CLI automatically falls back to `api_key`. Both fields can coexist.

**CRITICAL error:** if `api_key_env` contains the JWT value instead of the variable name, the CLI fails with:
`project "X" requires secret env eyJhbGci...`
Fix: set `api_key_env` to the variable name (e.g. `"APPLOGGER_SUPABASE_KEY"`).

---

## Multi-project configuration

```json
{
  "default_project": "klinema",
  "projects": [
    {
      "name": "klinema",
      "display_name": "Klinema Mobile",
      "workspace_roots": ["/workspace/klinema"],
      "supabase": {
        "url": "https://klinema.supabase.co",
        "api_key": "eyJhbGci..."
      }
    },
    {
      "name": "klinematv",
      "display_name": "Klinema TV",
      "workspace_roots": ["/workspace/klinematv"],
      "supabase": {
        "url": "https://klinematv.supabase.co",
        "api_key": "eyJhbGci..."
      }
    }
  ]
}
```

Project selection precedence:

1. `--project` flag
2. `APPLOGGER_PROJECT` env var
3. `workspace_roots` autodetection against current directory
4. `default_project`
5. Single configured project

---

## Optional `cli.json` fields (omit to use defaults)

- `schema`: PostgreSQL schema. Default: `public`
- `logs_table`: Logs table name. Default: `app_logs`
- `metrics_table`: Metrics table name. Default: `app_metrics`
- `timeout_seconds`: HTTP timeout (1-120). Default: `15`

Only set these if your Supabase migrations used non-default names.

---

## Telemetry query — what can be filtered

All query filters are CLI flags — they are NOT configured in `cli.json`. The json only defines the Supabase connection.

### `app_logs` — filterable columns

| Flag | Column / field | Match type | Notes |
|---|---|---|---|
| `--severity` | `level` (top-level) | exact, UPPERCASE | `debug`, `info`, `warn`, `error`, `critical`, `metric` |
| `--min-severity` | `level` (top-level) | `IN (level+)` | Captures level and all above. Mutually exclusive with `--severity`. |
| `--environment` | `environment` (top-level) | exact | `production`, `staging`, `development` |
| `--tag` | `tag` (top-level) | exact | UPPERCASE by convention: `AUTH`, `NETWORK`, `PAYMENT`, `PLAYER`, `BOOT` |
| `--session-id` | `session_id` (top-level) | exact UUID | |
| `--device-id` | `device_id` (top-level) | exact | |
| `--user-id` | `user_id` (top-level) | exact UUID | NULL by default, only populated with user consent |
| `--contains` | `message` (top-level) | ilike substring | case-insensitive |
| `--from` / `--to` | `created_at` (top-level) | gte / lte | RFC3339 |
| `--sdk-version` | `sdk_version` (top-level) | exact | e.g. `0.2.0` |
| `--anomaly-type` | `anomaly_type` (top-level) | exact | e.g. `slow_response`, `memory_leak` |
| `--package` | `extra->>package_name` (JSONB) | exact | e.g. `com.company.billing` |
| `--error-code` | `extra->>error_code` (JSONB) | exact | e.g. `E-42`, `AUTH_FAILED` |
| `--extra-key / --extra-value` | `extra->>KEY` (JSONB) | exact | Ad-hoc filter on any extra field. Both flags required together. |
| `--throwable` | adds `throwable_type`, `throwable_msg`, `stack_trace` | — | Flag only — adds columns to SELECT, no filter |
| `--offset` | — | — | Pagination offset (0-based). Use with `--limit`. |
| `--order` | `created_at` | `desc` or `asc` | Default: `desc` |

### `app_metrics` — filterable columns

| Flag | Column | Match type | Notes |
|---|---|---|---|
| `--name` | `name` (top-level) | exact | e.g. `response_time_ms`, `frame_drop_count` |
| `--environment` | `environment` (top-level) | exact | `production`, `staging`, `development` |
| `--session-id` | `session_id` (top-level) | exact UUID | |
| `--device-id` | `device_id` (top-level) | exact | |
| `--sdk-version` | `sdk_version` (top-level) | exact | e.g. `0.2.0` |
| `--from` / `--to` | `created_at` (top-level) | gte / lte | RFC3339 |
| `--offset` | — | — | Pagination offset (0-based) |
| `--order` | `created_at` | `desc` or `asc` | Default: `desc` |

### Aggregation modes

| Mode | Source | Groups by |
|---|:---:|---|
| `none` | both | No grouping — returns individual rows |
| `hour` | both | UTC hour truncated from `created_at` (e.g. `2026-03-23T10:00Z`) |
| `day` | both | UTC day (e.g. `2026-03-23`) |
| `week` | both | Monday of the week (e.g. `2026-03-23`) |
| `severity` | logs | `level` value |
| `tag` | logs | `tag` value |
| `session` | both | `session_id` |
| `name` | metrics | `name` |
| `environment` | both | `environment` value |

---

## Control variables (not deprecated — complement cli.json)

| Variable | Purpose |
|---|---|
| `APPLOGGER_CONFIG` | Override path to `cli.json` |
| `APPLOGGER_PROJECT` | Explicit project name selection |

---

## Validation workflow

1. Confirm `~/.apploggers/cli.json` exists and has `url` + `api_key` (or `api_key_env`) filled in.
2. Run readiness commands:

```bash
apploggers health --output json
apploggers telemetry query --source logs --limit 5 --output json
apploggers telemetry query --source metrics --limit 5 --output json
```

---

## Diagnosing configuration errors

| Error message | Cause | Fix |
|---|---|---|
| `requires secret env eyJhbGci...` | JWT value placed in `api_key_env` | Set `api_key_env` to the variable name; put the key in `api_key` |
| `missing Supabase URL` | `supabase.url` empty in `cli.json` | Add the Supabase project URL |
| `missing Supabase API key` | Both `api_key` and `api_key_env` empty/unresolved | Add `api_key` with the `service_role` key value |
| `project "X" not found` | Name mismatch | Check `name` fields in `cli.json` match exactly (case-insensitive) |
| `does not define any projects` | `projects` array is empty | Add at least one project entry |

---

## Gaps outside automation

1. Obtaining `service_role key` from Supabase Dashboard.
2. Infra-level service account creation.

---

## References bundled with this skill

1. `references/cli-live-setup-runbook.md`

---

## Output standard

1. Show what was configured and confirm `cli.json` path.
2. Show validation command results summary.
3. Report blockers clearly with actionable fix.
