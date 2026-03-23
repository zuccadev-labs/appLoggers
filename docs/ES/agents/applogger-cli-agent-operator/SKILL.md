# AppLogger CLI Agent Operator — AI Agent Skill

## Metadata

- **Skill ID**: `apploggers-agent-operator`
- **Version**: `2.0.0`
- **Category**: Production Operations, Telemetry Discovery, DevOps Automation
- **Complexity**: Advanced
- **Prerequisites**: CLI installed and configured, Supabase credentials, basic JSON/TOON parsing knowledge

---

## Purpose

This skill enables AI agents and automation systems to **safely, predictably, and deterministically** operate the AppLogger CLI for:

- **Telemetry queries** (logs, metrics, aggregations, pagination)
- **System health checks** (readiness + deep Supabase connectivity probe)
- **Contract discovery** (runtime capability detection)
- **Agent-to-agent communication** (compact TOON responses)
- **Production incident response** (root cause analysis, audit trails)
- **Real-time monitoring** (SSE stream for frontends, tail follow mode)
- **Statistical summaries** (error rate, top tags, by environment)

### Key Principle

> **Machine First, Audit Second**  
> All agent operations produce structured, machine-parseable output (JSON or TOON) suitable for further automation, logging, or escalation.

---

## Core Concepts

### 0. Instalacion bootstrap del CLI

Si `apploggers` no existe aun en la maquina del agente, instalar primero:

```bash
# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash
```

```powershell
# Windows PowerShell
irm https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.ps1 | iex
```

Despues verificar:

```bash
apploggers version --output json
```

### 1. Agent Contract Discovery

Before executing any command, **always** discover capabilities and schema:

```bash
# What can this CLI do?
apploggers capabilities --output json

# What fields exist in telemetry?
apploggers agent schema --output json

# Is the backend available? (basic probe)
apploggers health --output json

# Deep probe: real Supabase connectivity + latency
apploggers health --deep --output json
```

**Why?** The CLI may have new commands, output format changes, or schema updates between versions. Discovery ensures forward compatibility.

### 1.1 Multi-Project Resolution (Corporate Mode)

Resolution precedence:

1. `--project <name>`
2. `APPLOGGER_PROJECT`
3. Workspace autodetection via `workspace_roots`
4. `default_project`
5. Single configured project

Rules for agents:

- All configuration lives in `~/.apploggers/cli.json`.
- Use `api_key` for direct key storage (simplest for agents).
- Parse `project` and `config_source` from health/telemetry outputs for auditability.

### 2. Three Output Modes (Choose Wisely)

| Mode | Use When | Example |
|---|---|---|
| `--output text` | Never for agents | Humans reading |
| `--output json` | Strict JSON pipelines, cloud integrations | AWS Lambda, Google Cloud Functions |
| `--output agent` | Agent-to-agent, compact responses, TOON parsing | Preferred for AI agents |

**Rule**: Prefer `--output agent` (TOON format) for all agent operations. Fall back to `--output json` only if your parser cannot handle TOON.

### 3. Error Handling Contract

When a command fails, **always check stderr** for error envelope:

```bash
apploggers telemetry query \
  --source logs \
  --aggregate INVALID_MODE \
  --output json \
  2>&1

# stderr output
{
  "ok": false,
  "error": "invalid --aggregate value \"INVALID_MODE\" ...",
  "error_kind": "usage_error",
  "exit_code": 2
}
```

**Error Kinds**:
- `usage_error` (exit code 2): Fix the flags/args. Do NOT retry.
- `runtime_error` (exit code 1): System failure. The CLI retries 429/503 automatically (3 attempts, backoff 0s→2s→6s). If still failing, check `health --deep`.

**HTTP errors are now differentiated** — the error message tells you exactly what to fix:
- `authentication failed (HTTP 401)` → check `api_key` in `cli.json`
- `permission denied (HTTP 403)` → check RLS policies in Supabase
- `table not found (HTTP 404)` → run AppLoggers migrations
- `rate limited (HTTP 429)` → retried automatically
- `Supabase unavailable (HTTP 503)` → retried automatically

---

## Workflows

### Workflow 1: Pre-Flight Check (with Deep Probe)

```bash
#!/bin/bash

# 1. Verify CLI is installed
if ! command -v apploggers &> /dev/null; then
  echo "FATAL: apploggers not found in PATH"
  exit 127
fi

# 2. Verify CLI version compatibility
VERSION=$(apploggers version --output json | jq -r '.version // empty')
if [ -z "$VERSION" ]; then
  echo "FATAL: Cannot determine CLI version"
  exit 1
fi

# 3. Deep health check — verifies real Supabase connectivity
HEALTH=$(apploggers health --deep --output json)
if ! jq -e '.ok' <<< "$HEALTH" > /dev/null 2>&1; then
  echo "FATAL: Backend health check failed"
  echo "$HEALTH" | jq '{status, deep}'
  exit 1
fi

LATENCY=$(jq '.deep.latency_ms // "N/A"' <<< "$HEALTH")
echo "✓ Pre-flight check passed"
echo "  - CLI version: $VERSION"
echo "  - Project: $(jq -r '.project // "env"' <<< "$HEALTH")"
echo "  - Supabase latency: ${LATENCY}ms"
```

### Workflow 2: Production Incident Response

```bash
#!/bin/bash

# Quick stats — error rate in last hour
FROM=$(date -u -d '-1 hour' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -v-1H '+%Y-%m-%dT%H:%M:%SZ')

STATS=$(apploggers telemetry stats \
  --source logs \
  --environment production \
  --from "$FROM" \
  --output json)

ERROR_RATE=$(jq '.error_rate_pct' <<< "$STATS")
TOTAL=$(jq '.total_events' <<< "$STATS")

echo "Last hour: $TOTAL events, error rate: ${ERROR_RATE}%"

# If error rate > 20%, get details with stack traces
if (( $(echo "$ERROR_RATE > 20" | bc -l) )); then
  echo "HIGH ERROR RATE — fetching details..."
  apploggers telemetry query \
    --source logs \
    --min-severity error \
    --environment production \
    --from "$FROM" \
    --throwable \
    --limit 20 \
    --output json | jq '.rows[] | {level, tag, message, throwable_type, stack_trace[0]}'
fi
```

### Workflow 3: Session Reconstruction (Root Cause Analysis)

```bash
#!/bin/bash
SESSION_ID="${1}"
if [ -z "$SESSION_ID" ]; then echo "Usage: $0 <session-uuid>"; exit 2; fi

# All logs for session, ascending order (chronological)
LOGS=$(apploggers telemetry query \
  --source logs \
  --session-id "$SESSION_ID" \
  --order asc \
  --limit 1000 \
  --throwable \
  --output json)

# All metrics for session
METRICS=$(apploggers telemetry query \
  --source metrics \
  --session-id "$SESSION_ID" \
  --order asc \
  --limit 1000 \
  --output json)

echo "Session $SESSION_ID — $(jq '.count' <<< "$LOGS") logs, $(jq '.count' <<< "$METRICS") metrics"
jq -r '.rows[] | "\(.created_at) [\(.level)] \(.tag): \(.message)"' <<< "$LOGS"
```

### Workflow 4: Multi-Environment Comparison

```bash
#!/bin/bash
FROM=$(date -u -d '-24 hours' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -v-24H '+%Y-%m-%dT%H:%M:%SZ')

for ENV in production staging development; do
  STATS=$(apploggers telemetry stats \
    --source logs \
    --environment "$ENV" \
    --from "$FROM" \
    --output json 2>/dev/null)
  if [ $? -eq 0 ]; then
    TOTAL=$(jq '.total_events' <<< "$STATS")
    RATE=$(jq '.error_rate_pct' <<< "$STATS")
    echo "$ENV: $TOTAL events, ${RATE}% error rate"
  fi
done
```

### Workflow 5: Pagination for Large Datasets

```bash
#!/bin/bash
PAGE=0
PAGE_SIZE=1000
TOTAL_EXPORTED=0

while true; do
  RESULT=$(apploggers telemetry query \
    --source logs \
    --min-severity error \
    --environment production \
    --from 2026-03-23T00:00:00Z \
    --limit $PAGE_SIZE \
    --offset $((PAGE * PAGE_SIZE)) \
    --order asc \
    --output json)

  COUNT=$(jq '.count' <<< "$RESULT")
  echo "$RESULT" >> /tmp/all-errors.jsonl
  TOTAL_EXPORTED=$((TOTAL_EXPORTED + COUNT))
  echo "Page $PAGE: $COUNT rows (total: $TOTAL_EXPORTED)"

  [ "$COUNT" -lt "$PAGE_SIZE" ] && break
  PAGE=$((PAGE + 1))
done
echo "Export complete: $TOTAL_EXPORTED rows"
```

---

## Command Reference

### `apploggers health --deep`

**When to use**: Pre-flight check, incident response, monitoring cron.

```bash
apploggers health --deep --output json

# Example output:
# {
#   "ok": true,
#   "status": "ready",
#   "version": "0.2.0",
#   "project": "my-app",
#   "deep": {
#     "supabase_reachable": true,
#     "latency_ms": 42,
#     "logs_table_ok": true,
#     "metrics_table_ok": true
#   }
# }
```

### `apploggers telemetry query` — Full Flag Reference

```bash
apploggers telemetry query \
  --source logs|metrics \
  --from RFC3339 \
  --to RFC3339 \
  --aggregate none|hour|day|week|severity|tag|session|name|environment \
  --severity debug|info|warn|error|critical|metric \
  --min-severity debug|info|warn|error|critical \   # range: level >= min
  --environment production|staging|development \
  --tag TAG \
  --session-id UUID \
  --device-id ID \
  --user-id UUID \
  --contains TEXT \
  --package PACKAGE_NAME \
  --error-code CODE \
  --anomaly-type TYPE \
  --extra-key KEY --extra-value VALUE \             # JSONB ad-hoc filter
  --sdk-version VERSION \
  --throwable \                                     # include stack traces
  --name METRIC_NAME \
  --limit 1..1000 \
  --offset 0.. \                                    # pagination
  --order desc|asc \
  --output text|json|agent
```

> `--severity` and `--min-severity` are mutually exclusive.  
> `--extra-key` and `--extra-value` must be used together.

### `apploggers telemetry agent-response`

**Recommended for agents** — Compact TOON output with `rows_preview` and `hints`.

```bash
apploggers telemetry agent-response \
  --source logs \
  --min-severity error \
  --environment production \
  --aggregate severity \
  --preview-limit 3 \
  --output agent
```

### `apploggers telemetry stats`

**When to use**: Quick context before a detailed query. Error rate, top tags, distribution by environment.

```bash
apploggers telemetry stats \
  --source logs \
  --environment production \
  --from 2026-03-23T00:00:00Z \
  --output json
```

### `apploggers telemetry stream`

**When to use**: Frontend SSE integration. Pipe to an HTTP proxy that exposes it as `text/event-stream`.

```bash
apploggers telemetry stream \
  --source logs \
  --min-severity error \
  --environment production \
  --interval 5
```

### `apploggers telemetry tail`

**When to use**: Real-time debugging from terminal. Human-readable follow mode.

```bash
apploggers telemetry tail \
  --source logs \
  --min-severity error \
  --environment production \
  --interval 3
```

---

## TOON Format Parsing (Agent Output)

TOON (`toon-go v0.0.0-20251202084852`) is a line-oriented format with length markers. The CLI uses `toon.WithLengthMarkers(true)`.

### Bash

```bash
# Extract top-level key
count=$(apploggers telemetry agent-response --source logs --output agent | grep -E '^count:' | awk '{print $2}')
```

### Python

```python
import subprocess, json

# Use --output json for strict JSON parsing
result = subprocess.check_output([
    'apploggers', 'telemetry', 'agent-response',
    '--source', 'logs', '--aggregate', 'severity',
    '--output', 'json'
], text=True)
data = json.loads(result)
print(data['count'], data['summary'])
```

### Go

```go
type AgentResponse struct {
    Kind    string `json:"kind"`
    OK      bool   `json:"ok"`
    Source  string `json:"source"`
    Count   int    `json:"count"`
    Summary *struct {
        By      string `json:"by"`
        Buckets []struct {
            Key   string `json:"key"`
            Count int    `json:"count"`
        } `json:"buckets"`
    } `json:"summary,omitempty"`
}

cmd := exec.Command("apploggers", "telemetry", "agent-response",
    "--source", "logs", "--aggregate", "severity", "--output", "json")
out, _ := cmd.Output()
var resp AgentResponse
json.Unmarshal(out, &resp)
```

---

## Error Handling Matrix

| Scenario | Exit Code | Error Kind | Action |
|---|---|---|---|
| Invalid flag | 2 | `usage_error` | **Fail fast**. Fix invocation. |
| Invalid value (enum) | 2 | `usage_error` | **Fail fast**. Validate inputs against schema. |
| `--severity` + `--min-severity` together | 2 | `usage_error` | Use one or the other. |
| `--extra-key` without `--extra-value` | 2 | `usage_error` | Both flags required together. |
| Network timeout | 1 | `runtime_error` | CLI retries 429/503 automatically. Check `health --deep`. |
| HTTP 401 | 1 | `runtime_error` | Fix `api_key` in `cli.json`. |
| HTTP 403 | 1 | `runtime_error` | Fix RLS policies in Supabase. |
| HTTP 404 | 1 | `runtime_error` | Run AppLoggers migrations. |
| HTTP 429 | 1 | `runtime_error` | Auto-retried (3 attempts). |
| HTTP 503 | 1 | `runtime_error` | Auto-retried (3 attempts). |

---

## Best Practices

### 1. Use `--min-severity` instead of `--severity` for error triage

```bash
# ❌ Misses CRITICAL events
apploggers telemetry query --source logs --severity error

# ✅ Captures ERROR + CRITICAL
apploggers telemetry query --source logs --min-severity error
```

### 2. Always filter by `--environment` in production

```bash
# ❌ Mixes production and staging data
apploggers telemetry query --source logs --severity error

# ✅ Production only
apploggers telemetry query --source logs --min-severity error --environment production
```

### 3. Use `stats` before `query` for context

```bash
# Get the big picture first
apploggers telemetry stats --source logs --environment production --output json

# Then drill down
apploggers telemetry query --source logs --min-severity error --environment production --throwable --limit 20
```

### 4. Use `--throwable` only when needed

```bash
# ❌ Always fetching stack traces wastes bandwidth
apploggers telemetry query --source logs --throwable --limit 1000

# ✅ Only when debugging a specific error
apploggers telemetry query --source logs --severity error --throwable --limit 10
```

### 5. Paginate large exports

```bash
# ✅ Use --offset for datasets > 1000 rows
apploggers telemetry query --source logs --limit 1000 --offset 0
apploggers telemetry query --source logs --limit 1000 --offset 1000
```

### 6. Use `health --deep` in monitoring, not just `health`

```bash
# ❌ Only checks CLI readiness, not Supabase
apploggers health --output json

# ✅ Verifies real connectivity and measures latency
apploggers health --deep --output json
```

---

## Integration Examples

### GitHub Actions — Daily Audit

```yaml
name: Daily Telemetry Audit
on:
  schedule:
    - cron: '0 9 * * *'
jobs:
  audit:
    runs-on: ubuntu-latest
    steps:
      - name: Install CLI
        run: curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash

      - name: Configure cli.json
        run: |
          mkdir -p ~/.apploggers
          cat > ~/.apploggers/cli.json << EOF
          {
            "default_project": "my-app",
            "projects": [{"name": "my-app", "supabase": {
              "url": "${{ secrets.APPLOGGER_SUPABASE_URL }}",
              "api_key": "${{ secrets.APPLOGGER_SUPABASE_KEY }}"
            }}]
          }
          EOF

      - name: Deep health check
        run: apploggers health --deep --output json | jq -e '.ok'

      - name: Production stats
        run: |
          apploggers telemetry stats \
            --source logs --environment production \
            --from "$(date -u -d '-24 hours' '+%Y-%m-%dT%H:%M:%SZ')" \
            --output json > stats.json
          jq '{total: .total_events, error_rate: .error_rate_pct}' stats.json

      - name: Upload report
        uses: actions/upload-artifact@v4
        with:
          name: telemetry-audit
          path: stats.json
```

### Frontend SSE Proxy (Node.js)

```javascript
const { spawn } = require('child_process');
const http = require('http');

http.createServer((req, res) => {
  if (req.url !== '/stream') return res.end();
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'Access-Control-Allow-Origin': '*',
  });

  const cli = spawn('apploggers', [
    'telemetry', 'stream',
    '--source', 'logs',
    '--min-severity', 'error',
    '--environment', 'production',
    '--interval', '5',
  ]);

  cli.stdout.pipe(res);
  req.on('close', () => cli.kill());
}).listen(3001);
```

```javascript
// Browser client
const es = new EventSource('http://localhost:3001/stream');
es.addEventListener('telemetry', (e) => {
  const data = JSON.parse(e.data);
  console.log(`${data.count} new events`);
});
es.addEventListener('heartbeat', () => console.log('stream alive'));
es.addEventListener('error', (e) => {
  const data = JSON.parse(e.data);
  console.error('stream error:', data.error);
});
```

---

## Version Compatibility

| CLI Version | Contract Version | Go | Status |
|---|---|---|---|
| 0.2.0 | 2.0.0 | 1.24+ | Current |
| 0.1.x | 1.0.0 | 1.24+ | Deprecated |

Always use the latest stable release. Check [GitHub Releases](https://github.com/zuccadev-labs/appLoggers/releases).

---

**Help & Support**  
→ [GitHub Issues](https://github.com/zuccadev-labs/appLoggers/issues)  
→ [Discussions](https://github.com/zuccadev-labs/appLoggers/discussions)
