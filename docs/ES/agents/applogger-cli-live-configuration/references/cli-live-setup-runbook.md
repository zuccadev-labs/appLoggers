# CLI Live Setup Runbook

## Option A — Environment variables (simple / CI)

### Windows (PowerShell)

```powershell
$env:appLogger_supabaseUrl = "https://YOUR_PROJECT.supabase.co"
$env:appLogger_supabaseKey = "YOUR_SERVICE_ROLE_KEY"
apploggers health --output json
```

### Linux/macOS (bash)

```bash
export appLogger_supabaseUrl="https://YOUR_PROJECT.supabase.co"
export appLogger_supabaseKey="YOUR_SERVICE_ROLE_KEY"
apploggers health --output json
```

---

## Option B — Project config file (recommended)

File: `~/.apploggers/cli.json`

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

**CRITICAL:** `api_key_env` must be the **name** of the environment variable, not the key value.

```json
// ❌ WRONG
"api_key_env": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

// ✅ CORRECT
"api_key_env": "APPLOGGER_SUPABASE_KEY"
```

Then export the actual key:

```bash
# Linux / macOS
export APPLOGGER_SUPABASE_KEY="YOUR_SERVICE_ROLE_KEY"

# Windows PowerShell
$env:APPLOGGER_SUPABASE_KEY = "YOUR_SERVICE_ROLE_KEY"
```

Optional fields (omit to use defaults — `public`, `app_logs`, `app_metrics`, `15`):

```json
"schema": "public",
"logs_table": "app_logs",
"metrics_table": "app_metrics",
"timeout_seconds": 15
```

---

## Operational validation

1. `apploggers capabilities --output json`
2. `apploggers health --output json`
3. `apploggers telemetry query --source logs --limit 5 --output json`
4. `apploggers telemetry query --source metrics --limit 5 --output json`

---

## Production notes

1. Never store service_role value in repository files or in `api_key_env`.
2. Use secret manager for persistent storage.
3. Rotate keys and audit access periodically.
