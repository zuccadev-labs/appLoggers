#!/bin/bash
# ─────────────────────────────────────────────────
# Aplica las migraciones SQL en orden contra Supabase
# Uso: ./scripts/apply-migrations.sh <SUPABASE_URL> <SERVICE_ROLE_KEY>
# ─────────────────────────────────────────────────
set -euo pipefail

SUPABASE_URL="${1:?Error: falta SUPABASE_URL como primer argumento}"
SERVICE_KEY="${2:?Error: falta SERVICE_ROLE_KEY como segundo argumento}"
REST_URL="${SUPABASE_URL}/rest/v1/rpc"

echo "🔧 Aplicando migraciones a: ${SUPABASE_URL}"

for migration in migrations/*.sql; do
    echo "  ▸ Ejecutando: $(basename "$migration")"
    
    SQL_CONTENT=$(cat "$migration")
    
    # Usar la función rpc de Supabase para ejecutar SQL raw
    # Requiere que exista la función exec_sql en Supabase o usar la Management API
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
        "${SUPABASE_URL}/rest/v1/rpc/exec_sql" \
        -H "apikey: ${SERVICE_KEY}" \
        -H "Authorization: Bearer ${SERVICE_KEY}" \
        -H "Content-Type: application/json" \
        -d "{\"query\": $(echo "$SQL_CONTENT" | jq -Rs .)}")
    
    if [ "$HTTP_STATUS" = "404" ]; then
        echo "    ⚠ La función exec_sql no existe. Usa el SQL Editor de Supabase."
        echo "    📋 SQL copiado:"
        echo "    ────────────────────"
        head -5 "$migration"
        echo "    ..."
        break
    elif [ "$HTTP_STATUS" -ge 200 ] && [ "$HTTP_STATUS" -lt 300 ]; then
        echo "    ✓ OK (HTTP $HTTP_STATUS)"
    else
        echo "    ✗ Error (HTTP $HTTP_STATUS)"
    fi
done

echo ""
echo "✅ Migraciones completadas"
