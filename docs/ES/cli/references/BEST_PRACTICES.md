# AppLoggers CLI — Buenas Prácticas Corporativas

**Audiencia**: DevOps, SRE, DevEx Engineers, Architects

---

## Estándares de Producción

### 1. Configuración Segura

La configuración del CLI vive en `~/.apploggers/cli.json`. Este archivo es la fuente de verdad única — no hay variables de entorno que gestionar para uso local, agentes IA o procesos SSE.

#### ✅ Hacer

```json
{
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "supabase": {
        "url": "https://project.supabase.co",
        "api_key": "eyJhbGci..."
      }
    }
  ]
}
```

```bash
# Restringir permisos del archivo en Linux/macOS
chmod 600 ~/.apploggers/cli.json
```

> El CLI crea `cli.json` con permisos `0600` automáticamente en la primera ejecución. En Linux/macOS puedes verificarlo con `ls -la ~/.apploggers/cli.json`.

```bash
# Si se prefiere no almacenar el key en el archivo, usar api_key_env
# con el nombre de la variable UPPERCASE — el CLI la resuelve en runtime
# y cae a api_key si la variable no está exportada
```

```bash
# En Kubernetes: montar cli.json desde un Secret
kubectl create secret generic applogger-config \
  --from-file=cli.json=/path/to/cli.json
```

```yaml
# Pod config — montar el archivo en la ruta esperada
volumeMounts:
  - name: applogger-config
    mountPath: /root/.apploggers
    readOnly: true
volumes:
  - name: applogger-config
    secret:
      secretName: applogger-config
```

#### ❌ NO Hacer

```bash
# ❌ Versionar cli.json — contiene service_role key
git add ~/.apploggers/cli.json

# ❌ Hardcodear credenciales en scripts
apploggers --url "https://..." --key "eyJhbGc..."

# ❌ Loguear credenciales
echo "Using key: $(cat ~/.apploggers/cli.json | jq -r '.projects[0].supabase.api_key')"

# ❌ Usar anon/publishable key en cli.json — el CLI requiere service_role
```

---

### 2. Manejo de Errores

#### ✅ Hacer

```bash
#!/bin/bash
set -euo pipefail

if ! apploggers health --output json; then
  echo "ERROR: Health check failed (exit code: $?)" >&2
  echo "ERROR: $(date -u '+%Y-%m-%dT%H:%M:%SZ') - Backend unavailable" >> /var/log/applogger-health.log
  exit 1
fi

health_response=$(apploggers health --output json 2>&1)
if jq -e '.ok' <<< "$health_response" > /dev/null 2>&1; then
  echo "Health check passed"
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

# ❌ Parsear JSON sin validación
echo "$response" | jq '.count'
```

---

### 3. Validación de Entrada

#### ✅ Hacer

```bash
validate_source() {
  case "$1" in
    logs|metrics) return 0 ;;
    *) echo "ERROR: Invalid source '$1' (expected: logs, metrics)" >&2; return 2 ;;
  esac
}

validate_limit() {
  [ "$1" -ge 1 ] && [ "$1" -le 1000 ] || {
    echo "ERROR: Limit must be between 1 and 1000" >&2
    return 2
  }
}

validate_source "logs"
validate_limit "100"

apploggers telemetry query --source logs --limit 100
```

---

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
  apploggers capabilities --output json | tee "$CACHE_FILE"
}
```

---

### 5. Logging y Auditoría

#### ✅ Hacer

```bash
log_query() {
  local timestamp=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
  local user=$(whoami)
  local source="$1"

  echo "timestamp=$timestamp user=$user source=$source action=telemetry_query status=attempting" \
    >> /var/log/applogger-queries.log

  if apploggers telemetry query --source "$source" --output json; then
    echo "status=success" >> /var/log/applogger-queries.log
  else
    echo "status=failed" >> /var/log/applogger-queries.log
    return 1
  fi
}
```

#### ❌ NO Hacer

```bash
# ❌ Loguear contenido de cli.json — expone service_role key
cat ~/.apploggers/cli.json >> /var/log/debug.log
```

---

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

  # --deep verifica conectividad real a Supabase (latencia + tablas)
  if ! apploggers health --deep --output json | jq -e '.ok' > /dev/null; then
    echo "ALERT: Backend unavailable or degraded"
    echo "ALERT: Backend unavailable" | mail -s "AppLogger Down" "$ALERT_EMAIL"
    exit 1
  fi

  from=$(date -u -d '-24 hours' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || \
         date -u -v-24H '+%Y-%m-%dT%H:%M:%SZ')

  # Filtrar solo producción con severidad mínima ERROR
  error_count=$(apploggers telemetry query \
    --source logs --min-severity error --environment production \
    --from "$from" --output json | jq '.count')

  if [ "$error_count" -gt "$ALERT_THRESHOLD_ERRORS" ]; then
    echo "ALERT: High error rate ($error_count errors/24h)"
    echo "Errors: $error_count / Threshold: $ALERT_THRESHOLD_ERRORS" \
      | mail -s "AppLogger: High Error Rate" "$ALERT_EMAIL"
  fi

  # Stats rápidas para el dashboard
  apploggers telemetry stats \
    --source logs --environment production --from "$from" \
    --output json >> /var/log/applogger-stats.jsonl

  # NOTA: siempre especifica --from en stats. Sin él, el CLI agrega TODOS los
  # datos históricos (puede ser lento y semánticamente incorrecto para dashboards).

  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] Health check complete"
} >> "$LOG_FILE" 2>&1
```

#### Referencia de filtros disponibles (CLI 0.2.0)

| Flag | Aplica a | Descripción |
|---|---|---|
| `--severity` | logs | Nivel exacto: `debug`, `info`, `warn`, `error`, `critical` |
| `--min-severity` | logs | Nivel mínimo (incluye todos los superiores) |
| `--environment` | logs, metrics | Entorno: `production`, `staging`, `development` |
| `--from` / `--to` | logs, metrics | Rango temporal RFC3339 |
| `--tag` | logs | Filtro por tag exacto |
| `--session-id` | logs, metrics | Sesión específica |
| `--device-id` | logs, metrics | Dispositivo específico |
| `--sdk-version` | logs, metrics | Versión del SDK |
| `--anomaly-type` | logs | Tipo de anomalía top-level |
| `--throwable` | logs | Incluye `throwable_type`, `throwable_msg`, `stack_trace` |
| `--extra-key/--extra-value` | logs | Filtro JSONB ad-hoc en `extra` |
| `--offset` | logs, metrics | Paginación (saltar N registros) |
| `--order` | logs, metrics | `asc` o `desc` (default: `desc`) |
| `--name` | metrics | Nombre de métrica exacto |

---

### 7. Recuperación ante Fallos (Resilience)

#### ✅ Hacer

```bash
retry_with_backoff() {
  local max_attempts=3
  local timeout=5
  local attempt=1

  while [ $attempt -le $max_attempts ]; do
    if timeout $timeout "$@"; then
      return 0
    fi
    exitcode=$?
    [ $exitcode -eq 2 ] && return 2  # usage error — no retry

    if [ $attempt -lt $max_attempts ]; then
      delay=$((2 ** (attempt - 1)))
      echo "Failed. Retrying in ${delay}s..." >&2
      sleep $delay
    fi
    attempt=$((attempt + 1))
  done

  return $exitcode
}

retry_with_backoff apploggers telemetry query --source logs --limit 10 --output json
```

---

### 8. Integración con Sistemas Modernos

#### Kubernetes

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: applogger-telemetry-audit
spec:
  schedule: "0 9 * * *"
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
              curl -L https://github.com/zuccadev-labs/appLoggers/releases/latest/download/apploggers-linux-amd64 \
                -o /usr/local/bin/apploggers
              chmod +x /usr/local/bin/apploggers
              apploggers telemetry query \
                --source logs --aggregate severity \
                --output json > /tmp/audit.json
            volumeMounts:
              - name: applogger-config
                mountPath: /root/.apploggers
                readOnly: true
          volumes:
            - name: applogger-config
              secret:
                secretName: applogger-config
          restartPolicy: OnFailure
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
# Montar cli.json en /root/.apploggers/cli.json al ejecutar el contenedor
ENTRYPOINT ["apploggers"]
CMD ["--help"]
```

#### GitHub Actions

```yaml
name: Daily Telemetry Report

on:
  schedule:
    - cron: '0 8 * * *'

jobs:
  audit:
    runs-on: ubuntu-latest
    steps:
      - name: Download CLI
        run: |
          curl -L https://github.com/zuccadev-labs/appLoggers/releases/latest/download/apploggers-linux-amd64 \
            -o /tmp/apploggers
          chmod +x /tmp/apploggers

      - name: Configure cli.json
        run: |
          mkdir -p ~/.apploggers
          cat > ~/.apploggers/cli.json << 'EOF'
          {
            "default_project": "my-app",
            "projects": [
              {
                "name": "my-app",
                "supabase": {
                  "url": "${{ secrets.APPLOGGER_SUPABASE_URL }}",
                  "api_key": "${{ secrets.APPLOGGER_SUPABASE_KEY }}"
                }
              }
            ]
          }
          EOF

      - name: Generate report
        run: |
          /tmp/apploggers telemetry query \
            --source logs --aggregate severity \
            --output json > daily-report.json

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: daily-telemetry-report
          path: daily-report.json
```

---

## Checklist de Producción

- [ ] `~/.apploggers/cli.json` configurado con `url` y `api_key` (service_role)
- [ ] Archivo no versionado (en `.gitignore`)
- [ ] Permisos restringidos en Linux/macOS (`chmod 600`)
- [ ] Migraciones 001–009 aplicadas en Supabase (incluye columnas `environment`, `anomaly_type` e índices de rendimiento)
- [ ] Health check con `--deep` automatizado (cada 6 horas)
- [ ] Logging estructurado (JSON, no plaintext)
- [ ] Alertas configuradas con `--min-severity error --environment production`
- [ ] `telemetry stats` en cron para dashboard diario
- [ ] Retry logic implementado (exponential backoff)
- [ ] Rate limiting configurado
- [ ] Rotación de credenciales (30-90 días)
- [ ] Auditoría de accesos registrada

---

## Recursos

- [AppLoggers CLI — README](../README.md)
- [AppLoggers CLI — Installation](./INSTALLATION.md)
- [AppLoggers CLI — Supabase Configuration](./SUPABASE_CONFIGURATION.md)
- [Skill: CLI Agent Operator](../agents/applogger-cli-agent-operator/SKILL.md)
