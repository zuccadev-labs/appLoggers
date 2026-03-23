# CLI Live Setup Runbook

## Windows (PowerShell)

```powershell
$env:appLogger_supabaseUrl = "https://YOUR_PROJECT.supabase.co"
$env:appLogger_supabaseKey = "YOUR_SERVICE_ROLE_KEY"
apploggers health --output json
```

## Linux/macOS (bash)

```bash
export appLogger_supabaseUrl="https://YOUR_PROJECT.supabase.co"
export appLogger_supabaseKey="YOUR_SERVICE_ROLE_KEY"
apploggers health --output json
```

## Operational validation

1. `apploggers capabilities --output json`
2. `apploggers health --output json`
3. `apploggers telemetry query --source logs --limit 5 --output json`
4. `apploggers telemetry query --source metrics --name response_time_ms --limit 5 --output json`

## Production notes

1. Never store service_role in repository files.
2. Use secret manager for persistent storage.
3. Rotate keys and audit access periodically.
