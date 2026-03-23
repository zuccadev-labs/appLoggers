# AppLoggers CLI — Buenas Prácticas Corporativas

**Audiencia**: DevOps, SRE, DevEx Engineers, Architects

---

## Estándares de Producción

### 1. Configuración Segura

#### ✅ Hacer

```bash
# Usar variables de entorno (nunca hardcodear)
export appLogger_supabaseUrl="https://project.supabase.co"
export appLogger_supabaseKey="$(aws secretsmanager get-secret-value --secret-id applogger-key | jq -r .SecretString)"

apploggers health --output json
```

```bash
# En Kubernetes: usar Secrets
kubectl create secret generic applogger-credentials \
  --from-literal=supabase-url=https://... \
  --from-literal=supabase-key=...
```

```yaml
# Pod config
env:
  - name: appLogger_supabaseUrl
    valueFrom:
      secretKeyRef:
        name: applogger-credentials
        key: supabase-url
  - name: appLogger_supabaseKey
    valueFrom:
      secretKeyRef:
        name: applogger-credentials
        key: supabase-key
```

#### ❌ NO Hacer

```bash
# ❌ Hardcodear en scripts
apploggers --url "https://..." --key "eyJhbGc..."

# ❌ Guardar en archivos de configuración
echo "key=eyJhbGc..." > /etc/apploggers.conf   # ❌ ¡DANGEROUS!

# ❌ Pasar en línea de comandos visible
ps aux | grep apploggers                  # ❌ Secrets expuestos

# ❌ Loguear credenciales
echo "Using key: $appLogger_supabaseKey"    # ❌ Logs sensibles
```

### 2. Manejo de Errores

#### ✅ Hacer

```bash
#!/bin/bash
set -euo pipefail

# Check exit code
if ! apploggers health --output json; then
  echo "ERROR: Health check failed (exit code: $?)" >&2
  # Log properly but obscure sensitive data
  echo "ERROR: $(date -u '+%Y-%m-%dT%H:%M:%SZ') - Backend unavailable" >> /var/log/applogger-health.log
  exit 1
fi

# Parse JSON safely
health_response=$(apploggers health --output json 2>&1)
if jq -e '.ok' <<< "$health_response" > /dev/null 2>&1; then
  echo "✓ Health check passed"
else
  error_msg=$(jq -r '.error // "unknown error"' <<< "$health_response")
  echo "ERROR: $error_msg" >&2
  exit 1
fi
```

#### ❌ NO Hacer

```bash
# ❌ No verificar exit codes
apploggers telemetry query --source logs
# Si falla silenciosamente, continúa con datos inconsistentes

# ❌ No log de errores
apploggers health > /dev/null 2>&1  # Error desaparece

# ❌ Parsear JSON sin validación
echo "$response" | jq '.count'  # Si no es JSON válido, falla sin mensajes claros
```

### 3. Validación de Entrada

#### ✅ Hacer

```bash
# Whitelist de valores válidos
validate_source() {
  case "$1" in
    logs|metrics) return 0 ;;
    *) echo "ERROR: Invalid source '$1' (expected: logs, metrics)" >&2; return 2 ;;
  esac
}

validate_severity() {
  case "$1" in
    debug|info|warn|error) return 0 ;;
    *) echo "ERROR: Invalid severity '$1'" >&2; return 2 ;;
  esac
}

validate_limit() {
  [ "$1" -ge 1 ] && [ "$1" -le 1000 ] || {
    echo "ERROR: Limit must be between 1 and 1000" >&2
    return 2
  }
}

# Use validations
validate_source "logs"
validate_severity "error"
validate_limit "100"

apploggers telemetry query \
  --source logs \
  --severity error \
  --limit 100
```

#### ❌ NO Hacer

```bash
# ❌ Confiar ciegamente en entrada de usuario
SEVERITY="$1"
apploggers telemetry query --severity "$SEVERITY"
# Si el usuario pasa "--severity'; DROP TABLE;", puede causar problemas

# ❌ Límites sin validar
LIMIT="$1"
apploggers telemetry query --limit "$LIMIT"
# 1000000 puede sobrecargar el backend
```

### 4. Caching y Rate Limiting

#### ✅ Hacer

```bash
# Cache discovery results (24 horas)
CACHE_FILE="${HOME}/.cache/applogger-discovery"
CACHE_TTL=$((24 * 3600))

get_capabilities() {
  if [ -f "$CACHE_FILE" ]; then
    mtime=$(stat -c %Y "$CACHE_FILE" 2>/dev/null || stat -f %m "$CACHE_FILE")
    age=$(($(date +%s) - mtime))
    if [ $age -lt $CACHE_TTL ]; then
      cat "$CACHE_FILE"
      return 0
    fi
  fi
  
  # Refresh cache
  apploggers capabilities --output json | tee "$CACHE_FILE"
}

# Use cached capabilities
get_capabilities | jq '.capabilities'
```

```bash
# Rate limiting for queries
MAX_QUERIES_PER_MINUTE=10
QUERY_COUNT_FILE="/tmp/applogger-queries"

check_rate_limit() {
  current=$(date +%s)
  minute_ago=$((current - 60))
  
  # Count queries in last minute
  count=$(grep -c "^$minute_ago" "$QUERY_COUNT_FILE" 2>/dev/null || echo "0")
  
  if [ "$count" -ge $MAX_QUERIES_PER_MINUTE ]; then
    echo "ERROR: Rate limit exceeded (max $MAX_QUERIES_PER_MINUTE queries/min)" >&2
    return 1
  fi
  
  # Log this query
  echo "$current" >> "$QUERY_COUNT_FILE"
}

check_rate_limit && apploggers telemetry query --source logs
```

#### ❌ NO Hacer

```bash
# ❌ Llamadas sin caché
for i in {1..100}; do
  apploggers capabilities  # Hammering server 100x en loop
done

# ❌ Sin rate limiting
watch -n 1 'apploggers telemetry query'  # Consulta cada 1 segundo
```

### 5. Logging y Auditoría

#### ✅ Hacer

```bash
# Structured logging (JSON)
log_query() {
  local timestamp=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
  local user=$(whoami)
  local source="$1"
  
  {
    echo "timestamp=$timestamp"
    echo "user=$user"
    echo "source=$source"
    echo "action=telemetry_query"
    echo "status=attempting"
  } >> /var/log/applogger-queries.log
  
  # Execute query
  if apploggers telemetry query --source "$source" --output json; then
    echo "status=success" >> /var/log/applogger-queries.log
  else
    echo "status=failed" >> /var/log/applogger-queries.log
    return 1
  fi
}

log_query "logs"
```

#### ❌ NO Hacer

```bash
# ❌ Loguear sin estructura
apploggers telemetry query >> /tmp/log.txt  # Formato inconsistente

# ❌ Loguear datos sensibles
echo "Query ran with credentials: $appLogger_supabaseKey" >> log.txt  # ❌ SECRETOS

# ❌ Sin trazabilidad
apploggers health && echo "Health check OK"  # No se sabe quién, cuándo, por qué
```

### 6. Monitoreo en Producción

#### ✅ Hacer

```bash
#!/bin/bash
# Cron: 0 */6 * * * /usr/local/bin/applogger-health-monitor.sh

ALERT_THRESHOLD_ERRORS=500
ALERT_EMAIL="ops@company.com"
LOG_FILE="/var/log/applogger-monitor.log"

{
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] Running operational health check"
  
  # 1. Backend availability
  if ! apploggers health --output json | jq -e '.ok' > /dev/null; then
    echo "ALERT: Backend unavailable"
    echo "ALERT: Backend unavailable" | mail -s "AppLogger Down" "$ALERT_EMAIL"
    exit 1
  fi
  
  # 2. Error rate (last 24h)
  from=$(date -u -d '-24 hours' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || \
         date -u -v-24H '+%Y-%m-%dT%H:%M:%SZ')
  error_count=$(apploggers telemetry query \
    --source logs \
    --severity error \
    --from "$from" \
    --output json | jq '.count')
  
  if [ "$error_count" -gt "$ALERT_THRESHOLD_ERRORS" ]; then
    echo "ALERT: High error rate ($error_count errors/24h)"
    {
      echo "⚠️ High error rate detected"
      echo "Errors: $error_count"
      echo "Threshold: $ALERT_THRESHOLD_ERRORS"
    } | mail -s "AppLogger: High Error Rate" "$ALERT_EMAIL"
  fi
  
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] Health check complete"
} >> "$LOG_FILE" 2>&1
```

#### ❌ NO Hacer

```bash
# ❌ Sin monitoreo
# CLI cae silenciosamente, nadie se da cuenta

# ❌ Alertas sin contexto
apploggers health | grep -q "ok" || echo "ERROR"  # ¿Qué error? ¿Por qué?

# ❌ Monitoreo manual
# Ejecutar queries manualmente en lugar de automatizado
```

### 7. Recuperación ante Fallos (Resilience)

#### ✅ Hacer

```bash
# Retry with exponential backoff
retry_with_backoff() {
  local max_attempts=3
  local timeout=5  # seconds
  local attempt=1
  local exitcode=0
  
  while [ $attempt -le $max_attempts ]; do
    echo "[Attempt $attempt/$max_attempts] $@" >&2
    
    # Run command with timeout
    if timeout $timeout "$@"; then
      return 0
    fi
    exitcode=$?
    
    # Don't retry on usage errors (exit code 2)
    if [ $exitcode -eq 2 ]; then
      return 2
    fi
    
    if [ $attempt -lt $max_attempts ]; then
      delay=$((2 ** (attempt - 1)))  # 1, 2, 4 seconds
      echo "Failed. Retrying in ${delay}s..." >&2
      sleep $delay
    fi
    
    attempt=$((attempt + 1))
  done
  
  return $exitcode
}

# Use it
retry_with_backoff apploggers telemetry query --source logs --limit 10 --output json
```

#### ❌ NO Hacer

```bash
# ❌ Sin retry
apploggers telemetry query
# Si falla una vez, script falla completamente

# ❌ Infinite retry
while true; do
  apploggers telemetry query && break
done  # Si backend está caído, loops forever

# ❌ Retry inmediato
apploggers telemetry query || apploggers telemetry query  # Hammers endpoint
```

### 8. Integración con Sistemas Modernos

#### Kubernetes

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: applogger-telemetry-audit
spec:
  schedule: "0 9 * * *"  # 9 AM UTC daily
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: apploggers
            image: golang:1.25-alpine
            command:
            - sh
            - -c
            - |
              apk add --no-cache curl
              # Descargar la última release desde: https://github.com/zuccadev-labs/appLoggers/releases
              curl -L https://github.com/zuccadev-labs/appLoggers/releases/latest/download/apploggers-linux-amd64 \
                -o /usr/local/bin/apploggers
              chmod +x /usr/local/bin/apploggers
              apploggers telemetry query \
                --source logs \
                --aggregate severity \
                --output json > /tmp/audit.json
              echo "Audit complete"
            env:
            - name: appLogger_supabaseUrl
              valueFrom:
                secretKeyRef:
                  name: applogger-secrets
                  key: url
            - name: appLogger_supabaseKey
              valueFrom:
                secretKeyRef:
                  name: applogger-secrets
                  key: key
          restartPolicy: OnFailure
          serviceAccountName: applogger-reader
```

#### Docker / OCI

```dockerfile
FROM golang:1.25-alpine AS builder
RUN apk add --no-cache git
RUN git clone https://github.com/zuccadev-labs/appLoggers.git /app
WORKDIR /app/cli
RUN go build -o /usr/local/bin/apploggers ./cmd/applogger-cli

FROM alpine:latest
RUN apk add --no-cache ca-certificates
COPY --from=builder /usr/local/bin/apploggers /usr/local/bin/
ENV appLogger_supabaseUrl=""
ENV appLogger_supabaseKey=""
ENTRYPOINT ["apploggers"]
CMD ["--help"]
```

#### GitHub Actions

```yaml
name: Daily Telemetry Report

on:
  schedule:
    - cron: '0 8 * * *'  # 8 AM UTC

jobs:
  audit:
    runs-on: ubuntu-latest
    env:
      appLogger_supabaseUrl: ${{ secrets.APPLOGGER_SUPABASE_URL }}
      appLogger_supabaseKey: ${{ secrets.APPLOGGER_SUPABASE_KEY }}
    steps:
      - name: Download CLI
        run: |
          # Ver la última release en: https://github.com/zuccadev-labs/appLoggers/releases
          curl -L https://github.com/zuccadev-labs/appLoggers/releases/latest/download/apploggers-linux-amd64 \
            -o /tmp/apploggers
          chmod +x /tmp/apploggers
      
      - name: Generate report
        run: |
          /tmp/apploggers telemetry query \
            --source logs \
            --aggregate severity \
            --output json > daily-report.json
      
      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: daily-telemetry-report
          path: daily-report.json
```

---

## Checklist de Producción

- [ ] Variables de entorno configuradas (URL, Key)
- [ ] Credenciales en secrets manager (AWS Secrets Manager, Vault, K8s Secrets)
- [ ] Health check automatizado (cada 6 horas)
- [ ] Logging estructurado (JSON, no plaintext)
- [ ] Alertas configuradas (errores > threshold)
- [ ] Retry logic implementado (exponential backoff)
- [ ] Rate limiting configurado
- [ ] Documentación de runbooks
- [ ] Tests de integración locales
- [ ] Monitoreo de latencia (SLO)
- [ ] Rotación de credenciales (30-90 días)
- [ ] Auditoría de accesos registrada

---

## Recursos

- [AppLoggers CLI — README](../README.md)
- [AppLoggers CLI — Installation](./INSTALLATION.md)
- [AppLoggers CLI — Usage Guide](./README.md)
- [Skill: CLI Agent Operator](../agents/applogger-cli-agent-operator/SKILL.md)
