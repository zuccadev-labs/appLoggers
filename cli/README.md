# AppLogger CLI

AppLogger CLI is the command-line entry point for telemetry exploration and operational checks.

## Current Scope (Phase 1)

- Syncbin metadata contract via `--syncbin-metadata`
- Structured output mode via `--output text|json`
- POSIX-style exit codes:
  - `0` success
  - `1` runtime error
  - `2` usage error
- Baseline commands:
  - `version`
  - `capabilities`
  - `health` / `health --deep`
  - `agent schema`
  - `telemetry query` (Supabase-backed)
  - `telemetry stream` (SSE)
  - `telemetry tail` (follow mode)
  - `telemetry stats` (aggregation)
  - `remote-config list` / `set` / `delete`
  - `erase` (GDPR data erasure)
  - `verify` (batch integrity)
  - `upgrade`

## Agent-First Contract

For AI and automation clients:

1. Prefer `--output agent` for TOON-formatted compact responses (powered by `github.com/toon-format/toon-go`).
2. Use `--output json` when strict JSON pipelines are required.
3. Parse explicit fields only.
4. Use `capabilities` and `agent schema` for runtime discovery before invoking non-stable commands.
5. On errors with `--output json` or `--output agent`, parse `error_kind` and `exit_code` from stderr.

## Quick Start

```bash
cd cli
go mod tidy
go run ./cmd/applogger-cli --syncbin-metadata --output json
```

## Standard Installation

One-line installers for the latest published CLI release:

```bash
# Linux
curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash

# macOS
curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash
```

```powershell
# Windows PowerShell
irm https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.ps1 | iex
```

Notes:

- The bash installer auto-detects Linux vs macOS and `amd64` vs `arm64`.
- The PowerShell installer installs `applogger-cli.exe` into the user profile and adds it to the user `PATH`.
- Both installers resolve the latest `applogger-cli-v*` GitHub Release automatically.
- To pin a specific release, set `APPLOGGER_CLI_VERSION`, for example `APPLOGGER_CLI_VERSION=applogger-cli-v0.1.0`.

## Supabase Configuration (Environment Variables)

The CLI reads Supabase configuration from environment variables:

- `appLogger_supabaseUrl` (required)
- `appLogger_supabaseKey` (required, service_role key for CLI reads)
- `appLogger_supabaseSchema` (optional, default `public`)
- `appLogger_supabaseLogTable` (optional, default `app_logs`)
- `appLogger_supabaseMetricTable` (optional, default `app_metrics`)
- `appLogger_supabaseTimeoutSeconds` (optional, default `15`)

Fallback aliases are supported for compatibility:

- `APPLOGGER_SUPABASE_URL`
- `APPLOGGER_SUPABASE_KEY`
- `SUPABASE_URL`
- `SUPABASE_KEY`

## Multi-Project Configuration

For corporate setups with multiple telemetry apps, the CLI also supports a shared
project config file. This is the recommended model when the CLI will later be
hosted or orchestrated by a Wails desktop app and streamed over SSE.

Selection precedence:

1. `--project <name>`
2. `APPLOGGER_PROJECT`
3. Workspace autodetection via `workspace_roots`
4. `default_project`
5. Single configured project
6. Legacy environment variables (`appLogger_supabase*`, `APPLOGGER_SUPABASE_*`, `SUPABASE_*`)

Config file resolution:

- `--config <path>`
- `APPLOGGER_CONFIG`
- Default path: `$HOME/.apploggers/cli.json`
- Legacy fallback path: `os.UserConfigDir()/applogger/cli.json`

Recommended JSON structure:

```json
{
  "default_project": "klinema",
  "projects": [
    {
      "name": "klinema",
      "display_name": "Klinema Mobile",
      "workspace_roots": [
        "D:/workspace/klinema"
      ],
      "supabase": {
        "url": "https://klinema.supabase.co",
        "api_key_env": "APPLOGGER_KLINEMA_SUPABASE_KEY",
        "schema": "public",
        "logs_table": "app_logs",
        "metrics_table": "app_metrics",
        "timeout_seconds": 15
      }
    },
    {
      "name": "klinematv",
      "display_name": "Klinema TV",
      "workspace_roots": [
        "D:/workspace/klinematv"
      ],
      "supabase": {
        "url": "https://klinematv.supabase.co",
        "api_key_env": "APPLOGGER_KLINEMATV_SUPABASE_KEY"
      }
    }
  ]
}
```

Operational guidance:

- Keep `service_role` secrets outside the JSON file whenever possible by using `api_key_env`.
- Let Wails own the project registry and spawn the CLI with the same config model.
- SSE should transport resolved project context (`project`, `config_source`) rather than raw secrets.
- When only one project is configured, the CLI auto-selects it to keep local workflows simple.

### Export Variables (PowerShell)

```powershell
$env:appLogger_supabaseUrl="https://YOUR_PROJECT_REF.supabase.co"
$env:appLogger_supabaseKey="YOUR_SUPABASE_SERVICE_ROLE_KEY"
```

### Export Variables (CMD)

```cmd
set appLogger_supabaseUrl=https://YOUR_PROJECT_REF.supabase.co
set appLogger_supabaseKey=YOUR_SUPABASE_SERVICE_ROLE_KEY
```

### Export Variables (Bash/Zsh)

```bash
export appLogger_supabaseUrl="https://YOUR_PROJECT_REF.supabase.co"
export appLogger_supabaseKey="YOUR_SUPABASE_SERVICE_ROLE_KEY"
```

### If You Are Using Supabase MCP

You can resolve values before export with:

1. `mcp_supabase_get_project_url` for `appLogger_supabaseUrl`
2. `appLogger_supabaseKey` must be provisioned from secure secrets storage (service_role)

## Examples

```bash
# Version
applogger-cli version
applogger-cli version --output json

# Syncbin metadata
applogger-cli --syncbin-metadata
applogger-cli --syncbin-metadata --output json

# Capability and contract discovery
applogger-cli capabilities --output json
applogger-cli agent schema --output json

# Agent-native compact output (TOON format)
applogger-cli capabilities --output agent
applogger-cli telemetry query --output agent

# Dedicated compact orchestration response for agents
applogger-cli telemetry agent-response \
  --source logs \
  --aggregate severity \
  --preview-limit 5

# Health check for agents
applogger-cli health --output json

# Explicit project selection
applogger-cli --project klinema telemetry query --source logs --severity error --output json

# Workspace-based autodetection via APPLOGGER_CONFIG
APPLOGGER_CONFIG="$HOME/.apploggers/cli.json" applogger-cli telemetry query --source logs --limit 25 --output json

# Upgrade CLI to latest published release
applogger-cli upgrade

# Upgrade to an explicit release tag
applogger-cli upgrade --version applogger-cli-v0.1.1

# Minimal telemetry query
applogger-cli telemetry query

# Telemetry contract with filters
applogger-cli telemetry query \
  --source logs \
  --from 2026-03-01T00:00:00Z \
  --to 2026-03-02T00:00:00Z \
  --severity error \
  --aggregate hour \
  --limit 25 \
  --output json

# Query warning anomalies stored under extra.anomaly_type
applogger-cli telemetry query \
  --source logs \
  --anomaly-type slow_response \
  --limit 25 \
  --output json

# Query metrics source
applogger-cli telemetry query \
  --source metrics \
  --aggregate name \
  --session-id session-mobile-01 \
  --limit 50 \
  --output json

# Query logs with identity filters (session/device/user)
applogger-cli telemetry query \
  --source logs \
  --session-id session-mobile-01 \
  --device-id a13b8f3b-61f8-5a11-8a9d-6fdf3f5d1f2d \
  --user-id 6b5b0f7b-3fd5-5c2f-8f67-45d1a6f8f2dd \
  --limit 25 \
  --output json

# Query logs with package/error/message segmentation filters
applogger-cli telemetry query \
  --source logs \
  --package com.company.billing \
  --error-code E-42 \
  --contains timeout \
  --severity error \
  --limit 50 \
  --output json
```

### Aggregation Modes

- `none`: no aggregation summary
- `hour`: group by event hour (`created_at`)
- `session`: group by `session_id`
- `severity`: logs only, group by `level`
- `tag`: logs only, group by `tag`
- `name`: metrics only, group by metric `name`
- `day`: group by day UTC
- `week`: group by week (Monday)
- `environment`: group by environment (`production`, `staging`, `development`)

### Log Payload Notes

- Log queries include the `extra` object when present.
- `warn(..., anomalyType = "...")` is exposed through `extra.anomaly_type`.
- Use `--anomaly-type` to filter warning anomalies on the server side.
- `--session-id` now accepts any identifier string supported by your table schema.
- `--device-id` works for both `logs` and `metrics` sources.
- `--user-id` is available for `logs` source and maps to anonymized user identifiers.
- `--package` maps to `extra.package_name` for module/package-level segmentation in logs.
- `--error-code` maps to `extra.error_code` for operational error grouping.
- `--contains` applies `ilike` filtering over `message` for fast development triage.
- `--fingerprint` filters by SHA-256 pseudonymized device fingerprint via PostgREST JSONB: `extra->>device_fingerprint=eq.VALUE`. Logs only.
- Project-based responses include `project` and `config_source` when the CLI resolved a project profile.

### Remote Config Commands

Manage device-level SDK configuration overrides stored in `device_remote_config` table:

```bash
# List active remote configs
apploggers remote-config list --output json
apploggers remote-config list --environment production --output json
apploggers remote-config list --fingerprint "sha256..." --output json

# Set remote config for a device (by fingerprint) or globally
apploggers remote-config set \
  --fingerprint "sha256..." \
  --min-level warn \
  --sampling 0.5 \
  --debug false \
  --output json

# Delete remote config
apploggers remote-config delete --fingerprint "sha256..." --output json
apploggers remote-config delete --id 42 --output json
```

### GDPR Erasure Command

Delete user or device data across all tables (`app_logs`, `app_metrics`, `log_batches`):

```bash
# Erase by user ID
apploggers erase --user-id "user-123" --output json

# Erase by device ID
apploggers erase --device-id "device-456" --output json
```

### Health Deep Probe

`health --deep` now probes 5 tables: `app_logs`, `app_metrics`, `log_batches`, `device_remote_config`, `beta_testers`:

```bash
apploggers health --deep --output json
# Returns: supabase_reachable, latency_ms, logs_table_ok, metrics_table_ok,
#          log_batches_table_ok, remote_config_table_ok, beta_tester_table_ok
```

## Development

```bash
cd cli
go test ./...
```

## Next Milestones

- Phase 3: richer telemetry presets and saved reports
- Phase 4: installers and release automation

## Plugin Metadata

Syncbin plugin metadata lives in `plugin-metadata.yaml`.

## Release Distribution Contract

- Published binaries come from GitHub Releases tagged as `applogger-cli-v*`.
- Source of truth for CLI base version: `cli/VERSION`.
- Current release assets:
  - `applogger-cli-linux-amd64`
  - `applogger-cli-linux-arm64`
  - `applogger-cli-darwin-amd64`
  - `applogger-cli-darwin-arm64`
  - `applogger-cli-windows-amd64.exe`
  - `manifests/homebrew/applogger-cli.rb`
  - `manifests/scoop/applogger-cli.json`
  - `manifests/winget/DevZucca.AppLoggerCLI*.yaml`
- Each asset is accompanied by a `.sha256` checksum file.
- Package manager manifests are generated automatically on every `applogger-cli-v*` tag release.
