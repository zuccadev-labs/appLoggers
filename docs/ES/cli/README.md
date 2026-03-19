# AppLogger CLI — Guía de Uso y Referencia

**Última actualización**: 2026-03-19  
**Versión**: v0.1.0-alpha.0  
**Status**: Production-ready (beta)

---

## Introducción

**AppLogger CLI** es la herramienta de línea de comandos para consultar telemetría, validar salud del sistema, y automatizar operaciones en AppLogger. Está diseñada para humanos y agentes IA con igual prioridad.

### Características Principales

- ✅ **Syncbin-compatible** — Descubrimiento de metadatos automático
- ✅ **Tres modos de salida** — `text` (humano), `json` (parseable), `agent` (TOON compacto)
- ✅ **Supabase-backed** — Acceso a logs y métricas en tiempo real
- ✅ **Aggregations** — Síntesis por hora, severidad, tag, sesión, nombre
- ✅ **POSIX-compliant** — Códigos de salida estándar (0, 1, 2)

---

## Tabla de Contenidos

- [Instalación Rápida](#instalación-rápida)
- [Configuración Supabase Detallada](#configuración-supabase-detallada)
- [Comandos Principales](#comandos-principales)
- [Consultas de Telemetría](#consultas-de-telemetría)
- [Salidas (text/json/agent)](#salidas-texjsonarent)
- [Ejemplos Corporativos](#ejemplos-corporativos)
- [Integración con Agentes](#integración-con-agentes)
- [Variables de Entorno](#variables-de-entorno)
- [Códigos de Salida](#códigos-de-salida)
- [Reference](#reference)

---

## Instalación Rápida

Ver [INSTALLATION.md](./INSTALLATION.md) para:
- Windows, macOS, Linux
- Compilar desde fuente
- Docker

---

## Configuración Supabase Detallada

Para una configuración corporativa completa (migraciones, RLS, service_role,
usuario operativo del CLI, hardening y troubleshooting), ver:

- [SUPABASE_CONFIGURATION.md](./SUPABASE_CONFIGURATION.md)

```bash
# Linux / macOS
curl -L https://github.com/devzucca/appLoggers/releases/download/applogger-cli-v0.1.0/applogger-cli-linux-amd64 \
  -o /usr/local/bin/applogger-cli
chmod +x /usr/local/bin/applogger-cli

# Verificar
applogger-cli --version
```

---

## Comandos Principales

### `applogger-cli version`
Muestra la versión del CLI.

```bash
$ applogger-cli version
applogger-cli v0.1.0-alpha.0 (commit: abc1234, built: 2026-03-19T10:30:00Z)

# JSON
$ applogger-cli version --output json
{"ok": true, "version": "v0.1.0-alpha.0", "commit": "abc1234", "build_time": "2026-03-19T10:30:00Z"}
```

---

### `applogger-cli capabilities`
Muestra capacidades disponibles del CLI y modo agent soportado.

```bash
$ applogger-cli capabilities --output json
{
  "ok": true,
  "cli_name": "applogger-cli",
  "version": "v0.1.0-alpha.0",
  "output_modes": ["text", "json", "agent"],
  "capabilities": [
    "version",
    "capabilities",
    "health",
    "agent-schema",
    "telemetry-query",
    "telemetry-agent-response"
  ],
  "syncbin_contract": {
    "metadata": { "version": "v0.1.0-alpha.0" },
    "exit_codes": { "success": 0, "error": 1, "usage_error": 2 }
  }
}
```

---

### `applogger-cli health`
Verifica la salud del CLI y servicios backend.

```bash
$ applogger-cli health --output json
{
  "ok": true,
  "services": {
    "supabase": "available",
    "database": "online",
    "schema": "valid"
  },
  "timestamp": "2026-03-19T10:35:00Z"
}
```

---

### `applogger-cli agent schema`
Muestra el esquema de datos para consumo de agentes.

```bash
$ applogger-cli agent schema --output json
{
  "telemetry_log": {
    "fields": {
      "id": "uuid",
      "created_at": "timestamp",
      "level": "enum(debug|info|warn|error)",
      "message": "text",
      "session_id": "uuid",
      "tag": "text",
      "extra": "json"
    }
  },
  "telemetry_metric": {
    "fields": {
      "id": "uuid",
      "created_at": "timestamp",
      "name": "text",
      "value": "float64",
      "session_id": "uuid",
      "unit": "text"
    }
  }
}
```

---

## Consultas de Telemetría

### `applogger-cli telemetry query`

Consulta logs o métricas con filtrado y agregación opcionales.

#### Sintaxis

```bash
applogger-cli telemetry query \
  --source <logs|metrics> \
  [--from TIMESTAMP] \
  [--to TIMESTAMP] \
  [--aggregate MODE] \
  [--severity LEVEL] \          # logs only
  [--tag NAME] \                # logs only
  [--session-id UUID] \
  [--name METRIC_NAME] \        # metrics only
  [--limit N] \
  [--output FORMAT]
```

#### Parámetros

| Parámetro | Requerido | Valores | Ejemplo |
|---|:---:|---|---|
| `--source` | ✅ | `logs`, `metrics` | `--source logs` |
| `--from` | ❌ | RFC3339 | `--from 2026-03-01T00:00:00Z` |
| `--to` | ❌ | RFC3339 | `--to 2026-03-02T00:00:00Z` |
| `--aggregate` | ❌ | `none`, `hour`, `severity`, `tag`, `session`, `name` | `--aggregate severity` |
| `--severity` | ❌ (logs) | `debug`, `info`, `warn`, `error` | `--severity error` |
| `--tag` | ❌ (logs) | texto libre | `--tag PAYMENT` |
| `--session-id` | ❌ | UUID | `--session-id 550e8400-e29b-41d4-a716-446655440000` |
| `--name` | ❌ (metrics) | texto libre | `--name response_time_ms` |
| `--limit` | ❌ | 1-1000 (default: 25) | `--limit 50` |
| `--output` | ❌ | `text`, `json`, `agent` | `--output json` |

#### Ejemplos

**A. Consultar todos los logs de error (última semana)**
```bash
applogger-cli telemetry query \
  --source logs \
  --severity error \
  --limit 50
```

**B. Logs agregados por severidad (últimas 24 horas)**
```bash
applogger-cli telemetry query \
  --source logs \
  --aggregate severity \
  --from 2026-03-18T00:00:00Z \
  --to 2026-03-19T00:00:00Z \
  --output json
```

**C. Métricas de sesión específica**
```bash
applogger-cli telemetry query \
  --source metrics \
  --session-id 550e8400-e29b-41d4-a716-446655440000 \
  --aggregate name \
  --limit 100 \
  --output json
```

**D. Logs con tag PAYMENT (agregados por hora)**
```bash
applogger-cli telemetry query \
  --source logs \
  --tag PAYMENT \
  --aggregate hour \
  --from 2026-03-19T00:00:00Z \
  --to 2026-03-19T23:59:59Z
```

---

### `applogger-cli telemetry agent-response`

Salida **compacta optimizada para agentes IA y automatización**. (Version TOON)

#### Sintaxis

```bash
applogger-cli telemetry agent-response \
  --source <logs|metrics> \
  [--aggregate MODE] \
  [--from TIMESTAMP] \
  [--to TIMESTAMP] \
  [--severity LEVEL] \
  [--tag NAME] \
  [--session-id UUID] \
  [--name METRIC_NAME] \
  [--limit N] \
  [--preview-limit 0-50]
```

#### Parámetros Especiales

| Parámetro | Default | Valores | Propósito |
|---|---|---|---|
| `--preview-limit` | 1 | 0-50 | Filas de muestra en `rows_preview` |

#### Salida (TOON Format)

```toon
kind: telemetry_agent_response
ok: true
source: logs
count: 1247
summary:
  by: severity
  buckets:
    - {key: error, count: 567}
    - {key: warn, count: 680}
rows_preview:
  - {id: log_1, created_at: 2026-03-19T10:30:00Z, level: error, message: "Payment failed", session_id: "..."}
  - {id: log_2, created_at: 2026-03-19T10:29:15Z, level: error, message: "Timeout", session_id: "..."}
hint:
  - use_from_to_for_date_range
  - use_session_id_for_isolation
```

#### Ejemplo

```bash
$ applogger-cli telemetry agent-response \
  --source logs \
  --aggregate severity \
  --preview-limit 2

# Salida compacta TOON (mecanizado)
kind: telemetry_agent_response
ok: true
source: logs
count: 2145
summary: {by: severity, buckets: [{key: error, count: 1200}, {key: warn, count: 945}]}
rows_preview: [{id: ..., level: error, ...}, {id: ..., level: warn, ...}]
hints: [use_from_to_for_date_range]
```

---

## Salidas (text/json/agent)

El CLI soporta tres modos de salida:

### 1. **text** (Humano)

Por defecto, amigable para lectura.

```bash
$ applogger-cli telemetry query --source logs --severity error --limit 2

Total logs: 2145
Filter: severity=error
Showing 2 of 2145 results

[1] ERROR (2026-03-19T10:30:00Z) — PAYMENT
    Message: Transaction declined
    Session: 550e8400-e29b-41d4-a716-446655440000

[2] ERROR (2026-03-19T10:29:15Z) — AUTH
    Message: Invalid credentials
    Session: 550e8400-e29b-41d4-a716-446655440001
```

### 2. **json** (Parser/Script)

JSON valido compatible con cualquier lenguaje.

```json
{
  "ok": true,
  "source": "logs",
  "count": 2145,
  "request": {
    "source": "logs",
    "aggregate": "none",
    "severity": "error",
    "limit": 2
  },
  "rows": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "created_at": "2026-03-19T10:30:00Z",
      "level": "error",
      "message": "Transaction declined",
      "session_id": "550e8400-e29b-41d4-a716-446655440000",
      "tag": "PAYMENT",
      "extra": {}
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "created_at": "2026-03-19T10:29:15Z",
      "level": "error",
      "message": "Invalid credentials",
      "session_id": "550e8400-e29b-41d4-a716-446655440001",
      "tag": "AUTH",
      "extra": {}
    }
  ]
}
```

### 3. **agent** (TOON - Compacto/Máquina)

Formato TOON (JSON-like pero más compacto) para agentes IA.

```
ok: true
source: logs
count: 2145
rows_preview:
  - {id: "550e8400...", level: error, message: "Transaction declined", ...}
  - {id: "550e8400...", level: error, message: "Invalid credentials", ...}
hints: [use_from_to_for_date_range, check_severity_enum]
```

---

## Ejemplos Corporativos

### Caso 1: Audit Trail Post-Producción

**Escenario**: Al día siguiente de un release, auditar logs de error.

```bash
#!/bin/bash

YESTERDAY=$(date -u -d '-1 day' '+%Y-%m-%dT00:00:00Z')
TODAY=$(date -u '+%Y-%m-%dT00:00:00Z')

applogger-cli telemetry query \
  --source logs \
  --severity error \
  --from "$YESTERDAY" \
  --to "$TODAY" \
  --aggregate severity \
  --output json > /tmp/daily-errors.json

# Procesar JSON en tu sistema
jq '.rows | length' /tmp/daily-errors.json
```

### Caso 2: Monitoreo de Sesión de Usuario

**Escenario**: Un usuario reporta problema. Revisar toda su sesión.

```bash
SESSION_ID="550e8400-e29b-41d4-a716-446655440000"

applogger-cli telemetry query \
  --source logs \
  --session-id "$SESSION_ID" \
  --aggregate tag \
  --limit 100 \
  --output json | jq '.summary.buckets'
```

### Caso 3: Performance Baseline

**Escenario**: Comparar métricas de response time semana actual vs anterior.

```bash
# Semana anterior
LAST_WEEK_FROM=$(date -u -d '-14 days' '+%Y-%m-%dT00:00:00Z')
LAST_WEEK_TO=$(date -u -d '-7 days' '+%Y-%m-%dT00:00:00Z')

applogger-cli telemetry query \
  --source metrics \
  --name response_time_ms \
  --from "$LAST_WEEK_FROM" \
  --to "$LAST_WEEK_TO" \
  --aggregate name \
  --output json > /tmp/last-week.json

# Semana actual
THIS_WEEK_FROM=$(date -u -d '-7 days' '+%Y-%m-%dT00:00:00Z')
THIS_WEEK_TO=$(date -u '+%Y-%m-%dT00:00:00Z')

applogger-cli telemetry query \
  --source metrics \
  --name response_time_ms \
  --from "$THIS_WEEK_FROM" \
  --to "$THIS_WEEK_TO" \
  --aggregate name \
  --output json > /tmp/this-week.json

# Comparar con tu herramienta favorita
diff <(jq '.summary' /tmp/last-week.json) <(jq '.summary' /tmp/this-week.json)
```

### Caso 4: Alert Automático (Cron + Health Check)

**Escenario**: Daily health check y alerta si hay demasiados errores.

```bash
#!/bin/bash
# File: /usr/local/bin/applogger-health-check.sh

set -e

# Health check
if ! applogger-cli health --output json | jq -e '.ok' > /dev/null; then
  echo "ERROR: AppLogger health check failed" | mail -s "AppLogger Down" ops@company.com
  exit 1
fi

# Count errors in last 24h
ERROR_COUNT=$(applogger-cli telemetry query \
  --source logs \
  --severity error \
  --from "$(date -u -d '-24 hours' '+%Y-%m-%dT%H:%M:%SZ')" \
  --output json | jq '.count')

if [ "$ERROR_COUNT" -gt 1000 ]; then
  echo "WARNING: $ERROR_COUNT errors in last 24h" | mail -s "High Error Rate" ops@company.com
fi

echo "✓ AppLogger operates normally ($ERROR_COUNT errors/24h)"
```

```bash
# Agregar a crontab
# 0 9 * * * /usr/local/bin/applogger-health-check.sh
```

---

## Integración con Agentes

Ver [AGENT_OPERATOR_SKILL.md](../agents/applogger-cli-agent-operator/SKILL.md) para operación de agentes IA.

---

## Variables de Entorno

### Requeridas

| Variable | Propósito | Ejemplo |
|---|---|---|
| `APPLOGGER_SUPABASE_URL` | URL del proyecto | `https://project.supabase.co` |
| `APPLOGGER_SUPABASE_KEY` | API key service_role (solo backend/operaciones) | `eyJhbGc...` |

### Opcionales

| Variable | Default | Propósito |
|---|---|---|
| `APPLOGGER_SUPABASE_SCHEMA` | `public` | Esquema PostgreSQL |
| `APPLOGGER_SUPABASE_LOG_TABLE` | `app_logs` | Nombre tabla logs |
| `APPLOGGER_SUPABASE_METRIC_TABLE` | `app_metrics` | Nombre tabla métricas |
| `APPLOGGER_SUPABASE_TIMEOUT_SECONDS` | `15` | Timeout HTTP (1-120) |

### Backwards Compatibility

Fallback aliases para compatibilidad:
- `SUPABASE_URL` → `APPLOGGER_SUPABASE_URL`
- `SUPABASE_KEY` → `APPLOGGER_SUPABASE_KEY`

> Importante: el CLI requiere `service_role` para consultas `SELECT` con RLS activa.
> No uses anon/publishable key en `APPLOGGER_SUPABASE_KEY`.

---

## Códigos de Salida

| Código | Significado | Ejemplo |
|---|---|---|
| **0** | Éxito | `applogger-cli version` |
| **1** | Error en runtime | Red caída, Supabase no disponible |
| **2** | Error en uso/sintaxis | Flag inválido, valor fuera de rango |

### Ejemplo

```bash
$ applogger-cli telemetry query --aggregate INVALID_MODE
Error: invalid --aggregate value "INVALID_MODE" (expected: none, hour, severity, tag, session, name)
$ echo $?
2
```

---

## Reference

### Agregaciones Disponibles

| Modo | Fuente | Agrupa por | Ejemplo Salida |
|---|---|---|---|
| `none` | logs, metrics | — | Filas sin resumen |
| `hour` | logs, metrics | Hora (`created_at`) | `{by: hour, buckets: [{key: "2026-03-19T10", count: 42}, ...]}` |
| `severity` | logs | Nivel (`level`) | `{by: severity, buckets: [{key: error, count: 1200}, ...]}` |
| `tag` | logs | Tag (`tag`) | `{by: tag, buckets: [{key: PAYMENT, count: 300}, ...]}` |
| `session` | logs, metrics | Sesión (`session_id`) | `{by: session, buckets: [{key: "550e...", count: 15}, ...]}` |
| `name` | metrics | Nombre (`name`) | `{by: name, buckets: [{key: response_time_ms, count: 1000}, ...]}` |

---

### Formato de Timestamp

Los filtros `--from` y `--to` usan **RFC3339** (ISO 8601):

```
2026-03-19T10:30:45Z        ← UTC exacto
2026-03-19T10:30:45+00:00   ← UTC con offset
2026-03-19T10:30:45-05:00   ← Hora local (convertida a UTC internamente)
```

Herramientas útiles para timestamps:
```bash
# Fecha actual en RFC3339
date -u '+%Y-%m-%dT%H:%M:%SZ'

# Hace 24 horas
date -u -d '-24 hours' '+%Y-%m-%dT%H:%M:%SZ'  # GNU date (Linux)
date -u -v-24H '+%Y-%m-%dT%H:%M:%SZ'          # BSD date (macOS)
```

---

## Siguiente Paso

- **Instalación problemas**: Ver [INSTALLATION.md](./INSTALLATION.md)
- **Supabase + usuario CLI (detallado)**: Ver [SUPABASE_CONFIGURATION.md](./SUPABASE_CONFIGURATION.md)
- **Agentes IA**: Ver [../agents/applogger-cli-agent-operator/SKILL.md](../agents/applogger-cli-agent-operator/SKILL.md)
- **Desarrollo**: Ver [../../cli/README.md](../../cli/README.md)

---

**¿Preguntas?**  
→ [GitHub Issues](https://github.com/devzucca/appLoggers/issues)  
→ [Discussions](https://github.com/devzucca/appLoggers/discussions)
