# ─────────────────────────────────────────────────
# Aplica las migraciones SQL contra Supabase
# Uso: .\scripts\apply-migrations.ps1 -Url <SUPABASE_URL> -Key <SERVICE_ROLE_KEY>
# ─────────────────────────────────────────────────
param(
    [Parameter(Mandatory)][string]$Url,
    [Parameter(Mandatory)][string]$Key
)

$ErrorActionPreference = "Stop"
$migrations = Get-ChildItem -Path "migrations\*.sql" | Sort-Object Name

Write-Host "`n  Aplicando $($migrations.Count) migraciones a: $Url" -ForegroundColor Cyan

foreach ($migration in $migrations) {
    Write-Host "  >> $($ migration.Name)" -NoNewline
    
    $sql = Get-Content $migration.FullName -Raw
    
    $headers = @{
        "apikey"        = $Key
        "Authorization" = "Bearer $Key"
        "Content-Type"  = "application/json"
        "Prefer"        = "return=minimal"
    }
    
    $body = @{ query = $sql } | ConvertTo-Json -Depth 1
    
    try {
        $response = Invoke-RestMethod -Uri "$($Url.TrimEnd('/'))/rest/v1/rpc/exec_sql" `
            -Method Post -Headers $headers -Body $body -ErrorAction Stop
        Write-Host " OK" -ForegroundColor Green
    }
    catch {
        $status = $_.Exception.Response.StatusCode.value__
        if ($status -eq 404) {
            Write-Host "`n"
            Write-Host "  La funcion exec_sql no existe en Supabase." -ForegroundColor Yellow
            Write-Host "  Copia y ejecuta cada .sql manualmente en el SQL Editor de Supabase:" -ForegroundColor Yellow
            Write-Host ""
            foreach ($m in $migrations) {
                Write-Host "    $($m.Name)" -ForegroundColor White
            }
            Write-Host ""
            Write-Host "  O crea primero la funcion helper:" -ForegroundColor Yellow
            Write-Host '  CREATE OR REPLACE FUNCTION exec_sql(query TEXT) RETURNS VOID AS $$ BEGIN EXECUTE query; END; $$ LANGUAGE plpgsql SECURITY DEFINER;' -ForegroundColor DarkGray
            break
        }
        else {
            Write-Host " ERROR ($status): $_" -ForegroundColor Red
        }
    }
}

Write-Host "`n  Migraciones procesadas.`n" -ForegroundColor Cyan
