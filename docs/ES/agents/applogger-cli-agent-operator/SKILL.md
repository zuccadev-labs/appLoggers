# AppLogger CLI Agent Operator — AI Agent Skill

## Metadata

- **Skill ID**: `applogger-cli-agent-operator`
- **Version**: `1.0.0`
- **Category**: Production Operations, Telemetry Discovery, DevOps Automation
- **Complexity**: Advanced
- **Prerequisites**: CLI installed and configured, Supabase credentials, basic JSON/TOON parsing knowledge

---

## Purpose

This skill enables AI agents and automation systems to **safely, predictably, and deterministically** operate the AppLogger CLI for:

- **Telemetry queries** (logs, metrics, aggregations)
- **System health checks** (backend availability validation)
- **Contract discovery** (runtime capability detection)
- **Agent-to-agent communication** (compact TOON responses)
- **Production incident response** (root cause analysis, audit trails)

### Key Principle

> **Machine First, Audit Second**  
> All agent operations produce structured, machine-parseable output (JSON or TOON) suitable for further automation, logging, or escalation.

---

## Core Concepts

### 0. Instalacion bootstrap del CLI

Si `applogger-cli` no existe aun en la maquina del agente, instalar primero:

```bash
# Linux
curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash

# macOS (Intel / Apple Silicon)
curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash
```

```powershell
# Windows PowerShell
irm https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.ps1 | iex
```

Despues verificar:

```bash
applogger-cli version --output json
```

Reglas:

- Si el instalador cambia `PATH`, abrir una nueva shell o ejecutar por ruta absoluta una vez.
- Para fijar version: definir `APPLOGGER_CLI_VERSION=applogger-cli-vX.Y.Z` antes de instalar.
- En macOS y Linux el instalador valida checksums SHA-256 cuando las herramientas del sistema estan disponibles.

### 1. Agent Contract Discovery

Before executing any command, **always** discover capabilities and schema:

```bash
# What can this CLI do?
applogger-cli capabilities --output json

# What fields exist in telemetry?
applogger-cli agent schema --output json

# Is the backend available?
applogger-cli health --output json
```

**Why?** The CLI may have new commands, output format changes, or schema updates between versions. Discovery ensures forward compatibility.

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
# Example failure
applogger-cli telemetry query \
  --source logs \
  --aggregate INVALID_MODE \
  --output json \
  2>&1  # redirect stderr to parse errors

# stderr output
{
  "ok": false,
  "error": "invalid --aggregate value \"INVALID_MODE\" (expected: none, hour, severity, tag, session, name)",
  "error_kind": "usage_error",
  "exit_code": 2
}

# Check exit code
echo $?  # outputs: 2
```

**Error Kinds**:
- `usage_error` (exit code 2): Your invocation is wrong. Fix the flags/args.
- `runtime_error` (exit code 1): System failure (network, Supabase down). Retry with backoff.

---

## Workflows

### Workflow 1: Pre-Flight Check

**Guarantee**: Before querying telemetry, verify CLI + backend health.

```bash
#!/bin/bash

# 1. Verify CLI is installed
if ! command -v applogger-cli &> /dev/null; then
  echo "FATAL: applogger-cli not found in PATH"
  exit 127
fi

# 2. Verify credentials are set
if [ -z "$APPLOGGER_SUPABASE_URL" ] || [ -z "$APPLOGGER_SUPABASE_KEY" ]; then
  echo "FATAL: APPLOGGER_SUPABASE_URL or APPLOGGER_SUPABASE_KEY not set"
  exit 1
fi

# 3. Verify CLI version compatibility
VERSION=$(applogger-cli version --output json | jq -r '.version // empty')
if [ -z "$VERSION" ]; then
  echo "FATAL: Cannot determine CLI version"
  exit 1
fi

# 4. Verify backend is healthy
HEALTH=$(applogger-cli health --output json)
if ! jq -e '.ok' <<< "$HEALTH" > /dev/null 2>&1; then
  echo "FATAL: Backend health check failed"
  echo "$HEALTH" | jq .
  exit 1
fi

echo "✓ Pre-flight check passed"
echo "  - CLI version: $VERSION"
echo "  - Backend: $(jq '.services.supabase' <<< "$HEALTH")"
```

### Workflow 2: Safe Telemetry Query

**Guarantee**: Query with validation, error handling, and structured output.

```bash
#!/bin/bash

# Input validation
SOURCE="${1:-logs}"       # logs or metrics
SEVERITY="${2:-error}"    # error, warn, info, debug
LIMIT="${3:-100}"

# Validate input
if [[ "$SOURCE" != "logs" && "$SOURCE" != "metrics" ]]; then
  echo "ERROR: Invalid source '$SOURCE' (expected: logs, metrics)"
  exit 2
fi

# Build query (with date for relevance)
FROM=$(date -u -d '-24 hours' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || \
       date -u -v-24H '+%Y-%m-%dT%H:%M:%SZ')

# Execute query
QUERY=$(applogger-cli telemetry query \
  --source "$SOURCE" \
  --from "$FROM" \
  --severity "$SEVERITY" \
  --limit "$LIMIT" \
  --output agent \
  2>&1)

# Check exit code
if [ $? -ne 0 ]; then
  echo "ERROR: Query failed"
  echo "$QUERY" | jq . 2>/dev/null || echo "$QUERY"
  exit 1
fi

# Process result (agent output = TOON format)
echo "Query succeeded"
echo "$QUERY"

# Parse structured data (if TOON parsing available)
# Note: TOON is line-oriented key: value format
count=$(echo "$QUERY" | grep -E '^count:' | awk '{print $2}')
echo "Found $count records"
```

### Workflow 3: Incident Response Automation

**Guarantee**: Automatic incident detection and escalation.

```bash
#!/bin/bash

# Rule-based incident detection
detect_incident() {
  local hours_back="${1:-1}"
  
  # Query errors in last N hours
  local from=$(date -u -d "-${hours_back} hours" '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || \
               date -u -v-${hours_back}H '+%Y-%m-%dT%H:%M:%SZ')
  
  local resp=$(applogger-cli telemetry query \
    --source logs \
    --severity error \
    --aggregate hour \
    --from "$from" \
    --output json \
    2>&1)
  
  # Parse error count
  local count=$(jq '.count // 0' <<< "$resp")
  
  # Threshold: >500 errors per hour = incident
  if [ "$count" -gt 500 ]; then
    echo "INCIDENT DETECTED: $count errors in last $hours_back hour(s)"
    
    # Escalation: dump summary
    jq '.summary' <<< "$resp"
    
    # Escalation: notify (example: Slack webhook)
    curl -X POST "$SLACK_WEBHOOK" \
      -H 'Content-Type: application/json' \
      -d "{
        \"text\": \"🚨 AppLogger Incident: $count errors detected\",
        \"blocks\": [{\"type\": \"section\", \"text\": {\"type\": \"mrkdwn\", \"text\": \"$(jq '.summary' <<< "$resp" | jq -Rs .)\"}}}
      }"
    
    return 0  # incident detected
  else
    echo "✓ No incident: $count errors in last $hours_back hour(s)"
    return 1  # no incident
  fi
}

# Run detection
detect_incident 1
```

### Workflow 4: Session Reconstruction (Root Cause Analysis)

**Guarantee**: Full audit trail for a user session.

```bash
#!/bin/bash

# Input: User session ID
SESSION_ID="${1}"

if [ -z "$SESSION_ID" ]; then
  echo "Usage: $0 <session-uuid>"
  exit 2
fi

echo "Reconstructing session: $SESSION_ID"

# Get all logs for session ordered by time
LOGS=$(applogger-cli telemetry query \
  --source logs \
  --session-id "$SESSION_ID" \
  --limit 1000 \
  --output json)

if ! jq -e '.ok' <<< "$LOGS" > /dev/null; then
  echo "ERROR: Failed to fetch session logs"
  echo "$LOGS" | jq '.error'
  exit 1
fi

# Get all metrics for session
METRICS=$(applogger-cli telemetry query \
  --source metrics \
  --session-id "$SESSION_ID" \
  --limit 1000 \
  --output json)

# Combine into unified timeline
TIMELINE=$(jq -s 'add | .rows | sort_by(.created_at)' \
  <(jq '.rows[] | {type: "log", created_at, level, message}' <<< "$LOGS") \
  <(jq '.rows[] | {type: "metric", created_at, name, value}' <<< "$METRICS"))

# Output: Chronological audit trail
echo "Timeline:"
jq -r '.[] | "\(.created_at) [\(.type | ascii_upcase)] \(.level // .name): \(.message // .value)"' <<< "$TIMELINE"

# Save for evidence
TIMESTAMP=$(date -u '+%Y%m%d-%H%M%S')
jq . > "session-${SESSION_ID}-${TIMESTAMP}.json" <<< "$(jq -s '{session_id, logs: .[0], metrics: .[1]}' <(jq '.rows' <<< "$LOGS") <(jq '.rows' <<< "$METRICS"))"

echo "✓ Audit trail saved to: session-${SESSION_ID}-${TIMESTAMP}.json"
```

---

## Command Reference

### `applogger-cli capabilities`

**When to use**: At startup and periodically (hourly cache) to verify CLI features.

```bash
# Discover output modes
capabilities=$(applogger-cli capabilities --output json)

# Check if agent mode is supported
if jq -e '.output_modes[] | select(. == "agent")' <<< "$capabilities" > /dev/null; then
  echo "Agent mode supported ✓"
fi

# Check if telemetry is available
if jq -e '.capabilities[] | select(. == "telemetry-agent-response")' <<< "$capabilities" > /dev/null; then
  echo "Compact agent-response subcommand available ✓"
fi
```

### `applogger-cli health`

**When to use**: Every query (cached for 30 seconds) to detect transient failures.

```bash
# Full check
applogger-cli health --output json

# Example output:
# { "ok": true, "services": { "supabase": "available", "database": "online" } }

# Minimal check (just success/fail)
if applogger-cli health --output json | jq -e '.ok' > /dev/null; then
  echo "Backend healthy"
else
  echo "Backend degraded or unavailable"
fi
```

### `applogger-cli telemetry agent-response`

**Recommended for agents** — Compact TOON output for machine parsing.

```bash
# Preferred: Compact agent response with summary
applogger-cli telemetry agent-response \
  --source logs \
  --aggregate severity \
  --preview-limit 3 \
  --output agent

# Output example (TOON):
# kind: telemetry_agent_response
# ok: true
# source: logs
# count: 2145
# summary:
#   by: severity
#   buckets:
#     - {key: error, count: 1200}
#     - {key: warn, count: 945}
# rows_preview:
#   - {id: "...", level: error, message: "..."}
#   - {id: "...", level: warn, message: "..."}
#   - {id: "...", level: warn, message: "..."}
# hints:
#   - use_from_to_for_date_range
#   - prefer_session_id_for_isolation
```

### `applogger-cli telemetry query`

**When to use**: Full results needed, strict JSON required, analytics pipeline.

```bash
# Standard query
applogger-cli telemetry query \
  --source logs \
  --from 2026-03-19T00:00:00Z \
  --to 2026-03-19T23:59:59Z \
  --aggregate severity \
  --limit 25 \
  --output json

# Filter warning anomalies stored in extra.anomaly_type
applogger-cli telemetry query \
  --source logs \
  --severity warn \
  --anomaly-type slow_response \
  --limit 25 \
  --output json

# Piping for further processing
applogger-cli telemetry query \
  --source metrics \
  --name response_time_ms \
  --aggregate name \
  --output json \
  | jq '.summary.buckets | sort_by(-count) | .[0:5]'
```

---

## TOON Format Parsing (Agent Output)

TOON is a line-oriented format similar to YAML but more minimal. Here's how to parse agent responses in common languages:

### Bash (Pure)

```bash
# Extract a top-level key
count=$(grep -E '^count:' | awk '{print $2}')
kind=$(grep -E '^kind:' | awk '{print $2}')

# The CLI guarantees single-line, parseable format
```

### Python

```python
import subprocess
import json

# Execute query
output = subprocess.check_output([
    'applogger-cli', 'telemetry', 'agent-response',
    '--source', 'logs',
    '--aggregate', 'severity',
    '--output', 'agent'
], text=True)

# Simple TOON-to-dict parser
result = {}
for line in output.strip().split('\n'):
    if ':' in line:
        key, val = line.split(':', 1)
        res[key.strip()] = val.strip()

# Access
print(result['count'])
print(result['kind'])
```

### Go

```go
import (
	"encoding/json"
	"os/exec"
)

// Struct matching telemetry_agent_response
type AgentResponse struct {
	Kind   string `json:"kind"`
	OK     bool   `json:"ok"`
	Source string `json:"source"`
	Count  int    `json:"count"`
}

// Execute
cmd := exec.Command("applogger-cli", "telemetry", "agent-response",
	"--source", "logs",
	"--aggregate", "severity",
	"--output", "agent")

output, _ := cmd.Output()

// Parse (if using intermediary JSON conversion)
var resp AgentResponse
json.Unmarshal(output, &resp)
```

---

## Error Handling Matrix

| Scenario | Exit Code | Error Kind | Action |
|---|---|---|---|
| Invalid flag | 2 | `usage_error` | **Fail fast**. Fix invocation. |
| Invalid value (enum) | 2 | `usage_error` | **Fail fast**. Validate inputs against schema. |
| Invalid timestamp format | 2 | `usage_error` | **Fail fast**. Use RFC3339, use date utility. |
| Network timeout | 1 | `runtime_error` | **Retry with backoff** (exponential, max 3 attempts). |
| Supabase 5xx | 1 | `runtime_error` | **Retry with backoff**. Check `health` first. |
| Missing credentials | 1 | `runtime_error` | **Fail immediately**. Alert ops. |

### Example Retry Logic

```bash
#!/bin/bash

retry_query() {
  local cmd=("$@")
  local max_attempts=3
  local attempt=1
  local delay=1
  
  while [ $attempt -le $max_attempts ]; do
    echo "Attempt $attempt/$max_attempts..."
    
    "${cmd[@]}" && return 0
    
    exit_code=$?
    
    # Usage error: don't retry
    if [ $exit_code -eq 2 ]; then
      return 2
    fi
    
    # Runtime error: retry with backoff
    if [ $exit_code -eq 1 ] && [ $attempt -lt $max_attempts ]; then
      echo "Failed. Waiting ${delay}s before retry..."
      sleep $delay
      delay=$((delay * 2))  # exponential backoff
      attempt=$((attempt + 1))
    else
      return $exit_code
    fi
  done
}

retry_query applogger-cli telemetry query --source logs --limit 10 --output json
```

---

## Best Practices

### 1. **Always Validate Input Before Querying**

```bash
# ❌ Don't do this
applogger-cli telemetry query --source "$source_from_user" --limit "$limit_from_user"

# ✅ Do this
case "$source_from_user" in
  logs|metrics) source=$source_from_user ;;
  *) echo "ERROR: Invalid source"; exit 2 ;;
esac

case "$limit_from_user" in
  [0-9]*) [ "$limit_from_user" -ge 1 ] && [ "$limit_from_user" -le 1000 ] && \
          limit=$limit_from_user || exit 2 ;;
  *) echo "ERROR: Invalid limit"; exit 2 ;;
esac

applogger-cli telemetry query --source "$source" --limit $limit
```

### 2. **Cache Discovery Results (CloudFlare Workers Pattern)**

```bash
# discovery.json created once, reused for 24h
DISCOVERY_CACHE="${HOME}/.applogger-discovery"
CACHE_AGE=$((24 * 3600))

if [ -f "$DISCOVERY_CACHE" ]; then
  MTIME=$(stat -c %Y "$DISCOVERY_CACHE" 2>/dev/null || stat -f %m "$DISCOVERY_CACHE")
  AGE=$(($(date +%s) - MTIME))
  
  if [ $AGE -lt $CACHE_AGE ]; then
    echo "Using cached discovery (age: ${AGE}s)"
    cat "$DISCOVERY_CACHE"
    exit 0
  fi
fi

# Refresh
applogger-cli capabilities --output json > "$DISCOVERY_CACHE"
cat "$DISCOVERY_CACHE"
```

### 3. **Log All Queries for Audit**

```bash
# Every agent invocation should log:
log_file="/var/log/applogger-agent.log"

{
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] Query: $@"
  applogger-cli "$@" 2>&1
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] Exit: $?"
} >> "$log_file"
```

### 4. **Use Session-ID for Isolation**

```bash
# ❌ Risky: Query all logs
applogger-cli telemetry query --source logs --limit 5000

# ✅ Safe: Isolate by session
applogger-cli telemetry query \
  --source logs \
  --session-id "$user_session_id" \
  --limit 1000
```

### 5. **Prefer agent-response for Compact Output**

```bash
# ❌ Verbose: Full query response
applogger-cli telemetry query --source logs --aggregate severity --output json

# ✅ Compact: Agent-optimized
applogger-cli telemetry agent-response \
  --source logs \
  --aggregate severity \
  --preview-limit 2 \
  --output agent
```

---

## Integration Examples

### Example 1: Kubernetes Health Check

```yaml
# Pod liveness check
apiVersion: v1
kind: Pod
metadata:
  name: my-app
spec:
  containers:
  - name: app
    livenessProbe:
      exec:
        command:
        - sh
        - -c
        - applogger-cli health --output json | jq -e '.ok'
      initialDelaySeconds: 30
      periodSeconds: 10
```

### Example 2: GitHub Actions Workflow

```yaml
name: Daily Telemetry Audit

on:
  schedule:
    - cron: '0 9 * * *'  # 9 AM UTC

jobs:
  audit:
    runs-on: ubuntu-latest
    steps:
      - name: Install CLI
        run: |
          curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash

      - name: Query errors (last 24h)
        env:
          APPLOGGER_SUPABASE_URL: ${{ secrets.APPLOGGER_SUPABASE_URL }}
          APPLOGGER_SUPABASE_KEY: ${{ secrets.APPLOGGER_SUPABASE_KEY }}
        run: |
          /tmp/applogger-cli telemetry query \
            --source logs \
            --severity error \
            --output json \
            > audit-$(date +%Y%m%d).json

      - name: Upload report
        uses: actions/upload-artifact@v4
        with:
          name: telemetry-audit
          path: audit-*.json
```

### Example 3: Terraform + CLI Monitoring

```hcl
resource "null_resource" "applogger_health_check" {
  provisioners "local-exec" {
    command = "applogger-cli health --output json | jq '.ok'"
  }
  
  triggers = {
    always_run = timestamp()
  }
}
```

---

## Troubleshooting

### "applogger-cli: command not found"

```bash
# Check installation
which applogger-cli

# Check PATH
echo $PATH

# Reinstall
curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash
```

### "backend health check failed"

```bash
# Verify credentials
echo "URL: $APPLOGGER_SUPABASE_URL"
echo "Key: ${APPLOGGER_SUPABASE_KEY:0:10}..."

# Test connectivity
curl -s "$APPLOGGER_SUPABASE_URL/rest/v1/?apikey=$APPLOGGER_SUPABASE_KEY" | jq .

# Check Supabase dashboard
# https://supabase.com/dashboard → Status
```

### "TOON parse error"

TOON format is simple line-oriented format. Keys come before colons, values after:

```
kind: telemetry_agent_response
ok: true
count: 1247
```

If your parser shows errors, verify:
1. Output is actually TOON (not JSON with `--output json`)
2. Lines aren't wrapped/truncated
3. Use `applogger-cli telemetry agent-response --output agent 2>&1 | od -c` to debug bytes

---

## FAQ

**Q: Can I parse `--output agent` with JSON?**  
A: No, TOON is a different format. Use `--output json` for JSON parsing, or extract TOON one line at a time.

**Q: Is agent mode production-safe?**  
A: Yes. All agent responses are deterministic, validated, and tested against contract tests.

**Q: What happens if Supabase has 100s of millions of records?**  
A: Queries have `--limit` (default 25, max 1000). Use `--from` and `--to` to narrow date ranges.

**Q: Can I query sensitive PII?**  
A: Yes, but be responsible. The CLI has no filtering. Ensure your agent doesn't log sensitive data. Use RLS policies in Supabase.

**Q: Does the CLI cache results?**  
A: No. Each `applogger-cli` call hits Supabase. Cache in your agent if needed.

---

## Version Compatibility

| CLI Version | Node Version | Go | Status |
|---|---|---|---|
| 0.1.0-alpha.0+ | N/A | 1.24+ | Current |
| 0.2.0+ (planned) | N/A | 1.26+ | Future |

---

## Next Steps

1. **Install CLI**: Follow [../cli/INSTALLATION.md](../cli/INSTALLATION.md)
2. **Read CLI Guide**: [../cli/README.md](../cli/README.md)
3. **Try examples**: Run workflows above in your environment
4. **Integrate**: Use in your CI/CD, monitors, bots

---

**Help & Support**  
→ [GitHub Issues](https://github.com/devzucca/appLoggers/issues)  
→ [Discussions](https://github.com/devzucca/appLoggers/discussions)
