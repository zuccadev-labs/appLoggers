# CLI Live Setup Runbook

## Windows (PowerShell)

```powershell
$env:APPLOGGER_SUPABASE_URL = "https://YOUR_PROJECT.supabase.co"
$env:APPLOGGER_SUPABASE_KEY = "YOUR_SERVICE_ROLE_KEY"
applogger-cli health --output json
```

## Linux/macOS (bash)

```bash
export APPLOGGER_SUPABASE_URL="https://YOUR_PROJECT.supabase.co"
export APPLOGGER_SUPABASE_KEY="YOUR_SERVICE_ROLE_KEY"
applogger-cli health --output json
```

## Operational validation

1. `applogger-cli capabilities --output json`
2. `applogger-cli health --output json`
3. `applogger-cli telemetry query --source logs --limit 5 --output json`
4. `applogger-cli telemetry query --source metrics --name response_time_ms --limit 5 --output json`

## Production notes

1. Never store service_role in repository files.
2. Use secret manager for persistent storage.
3. Rotate keys and audit access periodically.
