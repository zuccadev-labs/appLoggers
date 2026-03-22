# AppLogger CLI — Guía de Uso y Referencia

**Última actualización**: 2026-03-22  
**Versión**: v0.1.2  
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
- [Selección de Proyecto](#selección-de-proyecto)
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

## Selección de Proyecto

El CLI ya soporta operación multi-proyecto para escenarios donde una misma
estación o futura app en Wails debe consultar telemetría de varias apps
distintas (`klinema`, `klinematv`, etc.).

Precedencia de resolución:

1. `--project`
2. `APPLOGGER_PROJECT`
3. Detección por `workspace_roots` desde `APPLOGGER_CONFIG`
4. `default_project`
5. Único proyecto configurado
6. Variables legacy `appLogger_supabase*`, `APPLOGGER_SUPABASE_*`, `SUPABASE_*`

Variables nuevas:

- `APPLOGGER_CONFIG`: ruta al archivo JSON de proyectos
- `APPLOGGER_PROJECT`: nombre del proyecto activo

Ejemplo rápido:

```bash
applogger-cli --project klinema telemetry query --source logs --severity error --output json
```

```bash
# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash

# Verificar
applogger-cli version --output json
```

```powershell
# Windows PowerShell
irm https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.ps1 | iex

# Verificar
applogger-cli version --output json
```

Notas:

- En macOS se usa el mismo instalador `bash`; detecta Intel vs Apple Silicon.
- En Linux detecta `amd64` vs `arm64`.
- En Windows instala en el perfil del usuario y actualiza `PATH`.

---

## Comandos Principales

### `applogger-cli upgrade`
Actualiza el binario del CLI a la ultima release publicada `applogger-cli-v*`.

```bash
applogger-cli upgrade
```

Version especifica:

```bash
applogger-cli upgrade --version applogger-cli-v0.1.1
```

Para reinstalar incluso si ya coincide la version actual:

```bash
applogger-cli upgrade --force
```

---

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
      "session_id": "text",
      "device_id": "text",
      "user_id": "text",
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
      "session_id": "text",
      "device_id": "text",
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
  [--anomaly-type TYPE] \       # logs only (extra.anomaly_type)
  [--session-id SESSION_ID] \
  [--device-id DEVICE_ID] \
  [--user-id USER_ID] \         # logs only
  [--package PACKAGE_NAME] \    # logs only (extra.package_name)
  [--error-code CODE] \         # logs only (extra.error_code)
  [--contains TEXT] \           # logs only (message ilike)
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
| `--severity` | ❌ (logs) | `debug`, `info`, `warn`, `error`, `critical`, `metric` | `--severity error` |
| `--tag` | ❌ (logs) | texto libre | `--tag PAYMENT` |
| `--anomaly-type` | ❌ (logs) | texto libre | `--anomaly-type slow_response` |
| `--session-id` | ❌ | texto libre | `--session-id session-mobile-01` |
| `--device-id` | ❌ | texto libre | `--device-id device-abc` |
| `--user-id` | ❌ (logs) | texto libre | `--user-id user-anon-001` |
| `--package` | ❌ (logs) | texto libre | `--package com.company.billing` |
| `--error-code` | ❌ (logs) | texto libre | `--error-code E-42` |
| `--contains` | ❌ (logs) | texto libre | `--contains timeout` |
| `--name` | ❌ (metrics) | texto libre | `--name response_time_ms` |
| `--limit` | ❌ | 1-1000 (default: 100) | `--limit 50` |
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
  --session-id session-mobile-01 \
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

**E. Warnings por tipo de anomalia**
```bash
applogger-cli telemetry query \
  --source logs \
  --severity warn \
  --anomaly-type slow_response \
  --limit 25 \
  --output json
```

**F. Segmentacion por paquete y error operativo**
```bash
applogger-cli telemetry query \
  --source logs \
  --package com.company.billing \
  --error-code E-42 \
  --contains timeout \
  --severity error \
  --limit 50 \
  --output json
```

Notas operativas:

- Las consultas de `logs` devuelven el campo `extra` cuando existe.
- `anomaly_type` no es una columna top-level; vive dentro de `extra.anomaly_type`.

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
  [--anomaly-type TYPE] \
  [--session-id SESSION_ID] \
  [--device-id DEVICE_ID] \
  [--user-id USER_ID] \
  [--name METRIC_NAME] \
  [--limit N] \
  [--preview-limit 0-50]
```

#### Parámetros Especiales

| Parámetro | Default | Valores | Propósito |
|---|---|---|---|
| `--preview-limit` | 5 | 0-50 | Filas de muestra en `rows_preview` |

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

source=logs
count=2
aggregate=none
from=
to=
severity=error
session_id=
tag=
name=
limit=2
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
    "anomaly_type": "",
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
SESSION_ID="session-mobile-01"

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
| `appLogger_supabaseUrl` | URL del proyecto | `https://project.supabase.co` |
| `appLogger_supabaseKey` | API key service_role (solo backend/operaciones) | `eyJhbGc...` |

### Opcionales

| Variable | Default | Propósito |
|---|---|---|
| `appLogger_supabaseSchema` | `public` | Esquema PostgreSQL |
| `appLogger_supabaseLogTable` | `app_logs` | Nombre tabla logs |
| `appLogger_supabaseMetricTable` | `app_metrics` | Nombre tabla métricas |
| `appLogger_supabaseTimeoutSeconds` | `15` | Timeout HTTP (1-120) |

### Backwards Compatibility

Fallback aliases para compatibilidad:

- `APPLOGGER_SUPABASE_URL` → `appLogger_supabaseUrl`
- `APPLOGGER_SUPABASE_KEY` → `appLogger_supabaseKey`
- `SUPABASE_URL` → `appLogger_supabaseUrl`
- `SUPABASE_KEY` → `appLogger_supabaseKey`

> Importante: el CLI requiere `service_role` para consultas `SELECT` con RLS activa.
> No uses anon/publishable key en `appLogger_supabaseKey`.

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
→ [GitHub Issues](https://github.com/zuccadev-labs/appLoggers/issues)  
→ [Discussions](https://github.com/zuccadev-labs/appLoggers/discussions)
