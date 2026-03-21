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
  - `health`
  - `agent schema`
  - `telemetry query` (Supabase-backed)

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
curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash

# macOS
curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash
```

```powershell
# Windows PowerShell
irm https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.ps1 | iex
```

Notes:

- The bash installer auto-detects Linux vs macOS and `amd64` vs `arm64`.
- The PowerShell installer installs `applogger-cli.exe` into the user profile and adds it to the user `PATH`.
- Both installers resolve the latest `applogger-cli-v*` GitHub Release automatically.
- To pin a specific release, set `APPLOGGER_CLI_VERSION`, for example `APPLOGGER_CLI_VERSION=applogger-cli-v0.1.0`.

## Supabase Configuration (Environment Variables)

The CLI reads Supabase configuration from environment variables:

- `APPLOGGER_SUPABASE_URL` (required)
- `APPLOGGER_SUPABASE_KEY` (required, service_role key for CLI reads)
- `APPLOGGER_SUPABASE_SCHEMA` (optional, default `public`)
- `APPLOGGER_SUPABASE_LOG_TABLE` (optional, default `app_logs`)
- `APPLOGGER_SUPABASE_METRIC_TABLE` (optional, default `app_metrics`)
- `APPLOGGER_SUPABASE_TIMEOUT_SECONDS` (optional, default `15`)

Fallback aliases are supported for compatibility:

- `SUPABASE_URL`
- `SUPABASE_KEY`

### Export Variables (PowerShell)

```powershell
$env:APPLOGGER_SUPABASE_URL="https://YOUR_PROJECT_REF.supabase.co"
$env:APPLOGGER_SUPABASE_KEY="YOUR_SUPABASE_SERVICE_ROLE_KEY"
```

### Export Variables (CMD)

```cmd
set APPLOGGER_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
set APPLOGGER_SUPABASE_KEY=YOUR_SUPABASE_SERVICE_ROLE_KEY
```

### Export Variables (Bash/Zsh)

```bash
export APPLOGGER_SUPABASE_URL="https://YOUR_PROJECT_REF.supabase.co"
export APPLOGGER_SUPABASE_KEY="YOUR_SUPABASE_SERVICE_ROLE_KEY"
```

### If You Are Using Supabase MCP

You can resolve values before export with:

1. `mcp_supabase_get_project_url` for `APPLOGGER_SUPABASE_URL`
2. `APPLOGGER_SUPABASE_KEY` must be provisioned from secure secrets storage (service_role)

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
  --session-id 00000000-0000-0000-0000-000000000000 \
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

### Log Payload Notes

- Log queries include the `extra` object when present.
- `warn(..., anomalyType = "...")` is exposed through `extra.anomaly_type`.
- Use `--anomaly-type` to filter warning anomalies on the server side.

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
- Current release assets:
  - `applogger-cli-linux-amd64`
  - `applogger-cli-linux-arm64`
  - `applogger-cli-darwin-amd64`
  - `applogger-cli-darwin-arm64`
  - `applogger-cli-windows-amd64.exe`
- Each asset is accompanied by a `.sha256` checksum file.
