# AppLoggers CLI — Guía de Uso y Referencia

**Última actualización**: 2026-03-22
**Plataformas soportadas**: Windows, macOS, Linux (x86_64, ARM64)

---

## Introducción

**AppLoggers CLI** es la herramienta de línea de comandos para consultar telemetría, validar salud del sistema y automatizar operaciones en AppLoggers. Está diseñada para humanos y agentes IA con igual prioridad.

### Características Principales

- ✅ **Syncbin-compatible** — Descubrimiento de metadatos automático
- ✅ **Tres modos de salida** — `text` (humano), `json` (parseable), `agent` (TOON compacto)
- ✅ **Supabase-backed** — Acceso a logs y métricas en tiempo real
- ✅ **Aggregations** — Síntesis por hora, severidad, tag, sesión, nombre
- ✅ **POSIX-compliant** — Códigos de salida estándar (0, 1, 2)
- ✅ **Multi-proyecto** — Gestión de múltiples apps desde un único archivo de configuración

---

## Tabla de Contenidos

- [Instalación Rápida](#instalación-rápida)
- [Configuración](#configuración)
- [Selección de Proyecto](#selección-de-proyecto)
- [Comandos Principales](#comandos-principales)
- [Consultas de Telemetría](#consultas-de-telemetría)
- [Modos de Salida](#modos-de-salida)
- [Ejemplos Corporativos](#ejemplos-corporativos)
- [Integración con Agentes](#integración-con-agentes)
- [Variables de Entorno](#variables-de-entorno)
- [Códigos de Salida](#códigos-de-salida)
- [Referencia](#referencia)

---

## Instalación Rápida

```bash
# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.sh | bash
```

```powershell
# Windows PowerShell
irm https://raw.githubusercontent.com/zuccadev-labs/appLoggers/main/cli/install/install.ps1 | iex
```

Ver [INSTALLATION.md](./INSTALLATION.md) para instalación manual, compilación desde fuente y Docker.

---

## Configuración

El CLI resuelve la configuración de Supabase en este orden de prioridad:

1. Archivo de proyectos `~/.apploggers/cli.json` (recomendado)
2. Variables de entorno directas

### Opción recomendada: archivo de proyectos

Al instalar o ejecutar el CLI por primera vez, se crea automáticamente `~/.apploggers/cli.json` con un template de ejemplo. Edita ese archivo con los datos de tu proyecto:

```json
{
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "display_name": "My Application",
      "workspace_roots": [
        "/home/user/workspace/my-app"
      ],
      "supabase": {
        "url": "https://your-project.supabase.co",
        "api_key_env": "APPLOGGER_SUPABASE_KEY",
        "schema": "public",
        "logs_table": "app_logs",
        "metrics_table": "app_metrics",
        "timeout_seconds": 15
      }
    }
  ]
}
```

#### Campos del archivo de proyectos

| Campo | Requerido | Descripción |
|---|:---:|---|
| `default_project` | ✅ | Nombre del proyecto que se usa cuando no hay otro criterio de selección |
| `projects[].name` | ✅ | Identificador único del proyecto (usado con `--project` o `APPLOGGER_PROJECT`) |
| `projects[].display_name` | ❌ | Nombre legible para humanos (solo informativo) |
| `projects[].workspace_roots` | ❌ | Rutas locales del workspace. Si el directorio actual está dentro de alguna de estas rutas, el proyecto se selecciona automáticamente |
| `supabase.url` | ✅ | URL del proyecto Supabase (ej: `https://xxxx.supabase.co`) |
| `supabase.api_key_env` | ✅* | **Nombre** de la variable de entorno que contiene el `service_role` key. El CLI lee `os.Getenv(api_key_env)` en tiempo de ejecución. Nunca pongas el valor del JWT aquí — pon el nombre de la variable |
| `supabase.api_key` | ✅* | Valor directo del `service_role` key. Solo usar en entornos donde no es posible usar variables de entorno. Evitar en archivos versionados |
| `supabase.schema` | ❌ | Esquema PostgreSQL donde están las tablas. Default: `public`. Cambiar solo si migraste las tablas a un esquema custom |
| `supabase.logs_table` | ❌ | Nombre de la tabla de logs. Default: `app_logs`. Cambiar solo si usaste un nombre distinto en las migraciones |
| `supabase.metrics_table` | ❌ | Nombre de la tabla de métricas. Default: `app_metrics`. Cambiar solo si usaste un nombre distinto en las migraciones |
| `supabase.timeout_seconds` | ❌ | Timeout HTTP en segundos (1-120). Default: `15` |

> `api_key_env` y `api_key` son mutuamente excluyentes. Si ambos están presentes, `api_key_env` tiene precedencia.

#### Error frecuente — `api_key_env` con el valor del JWT

```json
// ❌ INCORRECTO — api_key_env recibe el nombre de la variable, no el valor
"api_key_env": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

// ✅ CORRECTO — api_key_env recibe el nombre de la variable de entorno
"api_key_env": "APPLOGGER_SUPABASE_KEY"
```

Luego exportás la variable en tu shell antes de usar el CLI:

```bash
# Linux / macOS
export APPLOGGER_SUPABASE_KEY="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Windows PowerShell
$env:APPLOGGER_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

#### Ejemplo multi-proyecto

```json
{
  "default_project": "klinema",
  "projects": [
    {
      "name": "klinema",
      "display_name": "Klinema Mobile",
      "workspace_roots": [
        "D:/workspace/klinema-app"
      ],
      "supabase": {
        "url": "https://klinema.supabase.co",
        "api_key_env": "APPLOGGER_KLINEMA_KEY"
      }
    },
    {
      "name": "klinematv",
      "display_name": "Klinema TV",
      "workspace_roots": [
        "D:/workspace/klinematv"
      ],
      "supabase": {
        "url": "https://klinematv.supabase.co",
        "api_key_env": "APPLOGGER_KLINEMATV_KEY"
      }
    }
  ]
}
```

Cuando `schema`, `logs_table` y `metrics_table` se omiten, el CLI usa los defaults (`public`, `app_logs`, `app_metrics`). Solo especificalos si tu setup de Supabase usa nombres distintos a los de las migraciones estándar del proyecto.

Usando `api_key_env` el secreto nunca queda en el archivo — se lee desde la variable de entorno que indiques.

### Opción alternativa: variables de entorno

Para entornos simples o CI/CD sin archivo de proyectos:

```bash
export appLogger_supabaseUrl="https://your-project.supabase.co"
export appLogger_supabaseKey="your-service-role-key"
```

Ver la sección [Variables de Entorno](#variables-de-entorno) para la lista completa.

Para configuración corporativa completa (migraciones, RLS, hardening), ver [SUPABASE_CONFIGURATION.md](./SUPABASE_CONFIGURATION.md).

---

## Selección de Proyecto

El CLI soporta operación multi-proyecto. Precedencia de resolución:

1. `--project <nombre>`
2. `APPLOGGER_PROJECT`
3. Detección automática por `workspace_roots` en `~/.apploggers/cli.json`
4. `default_project` en el archivo de configuración
5. Único proyecto configurado
6. Variables de entorno legacy (`appLogger_supabase*`, `APPLOGGER_SUPABASE_*`, `SUPABASE_*`)

```bash
# Selección explícita de proyecto
apploggers --project my-app telemetry query --source logs --severity error --output json
```

---

## Comandos Principales

### `apploggers upgrade`

Actualiza el binario a la última release publicada.

```bash
apploggers upgrade
```

Versión específica:

```bash
apploggers upgrade --version apploggers-vX.Y.Z
```

Forzar reinstalación aunque ya coincida la versión:

```bash
apploggers upgrade --force
```

---

### `apploggers version`

Muestra la versión del CLI.

```bash
apploggers version
apploggers version --output json
```

---

### `apploggers capabilities`

Muestra capacidades disponibles del CLI y contratos de automatización.

```bash
apploggers capabilities --output json
```

---

### `apploggers health`

Verifica la salud del CLI y servicios backend.

```bash
apploggers health --output json
```

---

### `apploggers agent schema`

Muestra el esquema de datos para consumo de agentes.

```bash
apploggers agent schema --output json
```

---

## Consultas de Telemetría

### `apploggers telemetry query`

Consulta logs o métricas con filtrado y agregación opcionales.

#### Sintaxis

```bash
apploggers telemetry query \
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
| `--from` | ❌ | RFC3339 | `--from 2026-01-01T00:00:00Z` |
| `--to` | ❌ | RFC3339 | `--to 2026-01-02T00:00:00Z` |
| `--aggregate` | ❌ | `none`, `hour`, `severity`, `tag`, `session`, `name` | `--aggregate severity` |
| `--severity` | ❌ (logs) | `debug`, `info`, `warn`, `error`, `critical`, `metric` | `--severity error` |
| `--tag` | ❌ (logs) | texto libre | `--tag PAYMENT` |
| `--anomaly-type` | ❌ (logs) | texto libre | `--anomaly-type slow_response` |
| `--session-id` | ❌ | texto libre | `--session-id <uuid>` |
| `--device-id` | ❌ | texto libre | `--device-id <id>` |
| `--user-id` | ❌ (logs) | texto libre | `--user-id <id>` |
| `--package` | ❌ (logs) | texto libre | `--package com.company.billing` |
| `--error-code` | ❌ (logs) | texto libre | `--error-code E-42` |
| `--contains` | ❌ (logs) | texto libre | `--contains timeout` |
| `--name` | ❌ (metrics) | texto libre | `--name response_time_ms` |
| `--limit` | ❌ | 1-1000 (default: 100) | `--limit 50` |
| `--output` | ❌ | `text`, `json`, `agent` | `--output json` |

#### Ejemplos

**A. Logs de error recientes**
```bash
apploggers telemetry query \
  --source logs \
  --severity error \
  --limit 50
```

**B. Logs agregados por severidad en un rango de fechas**
```bash
apploggers telemetry query \
  --source logs \
  --aggregate severity \
  --from 2026-01-01T00:00:00Z \
  --to 2026-01-02T00:00:00Z \
  --output json
```

**C. Métricas de una sesión específica**
```bash
apploggers telemetry query \
  --source metrics \
  --session-id <session-uuid> \
  --aggregate name \
  --limit 100 \
  --output json
```

**D. Logs con tag PAYMENT agregados por hora**
```bash
apploggers telemetry query \
  --source logs \
  --tag PAYMENT \
  --aggregate hour \
  --from 2026-01-01T00:00:00Z \
  --to 2026-01-01T23:59:59Z
```

**E. Warnings por tipo de anomalía**
```bash
apploggers telemetry query \
  --source logs \
  --severity warn \
  --anomaly-type slow_response \
  --limit 25 \
  --output json
```

**F. Segmentación por paquete y código de error**
```bash
apploggers telemetry query \
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

### `apploggers telemetry agent-response`

Salida compacta optimizada para agentes IA y automatización (formato TOON).

#### Sintaxis

```bash
apploggers telemetry agent-response \
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

| Parámetro | Default | Propósito |
|---|---|---|
| `--preview-limit` | 5 | Filas de muestra en `rows_preview` |

#### Ejemplo

```bash
apploggers telemetry agent-response \
  --source logs \
  --aggregate severity \
  --preview-limit 3 \
  --output agent
```

---

## Modos de Salida

El CLI soporta tres modos con `--output`:

| Modo | Uso | Descripción |
|---|---|---|
| `text` | Humanos | Formato legible por defecto |
| `json` | Scripts / pipelines | JSON válido, compatible con cualquier lenguaje |
| `agent` | Agentes IA / automatización | Formato TOON compacto, optimizado para parseo |

---

## Ejemplos Corporativos

### Audit Trail Post-Producción

```bash
#!/bin/bash
YESTERDAY=$(date -u -d '-1 day' '+%Y-%m-%dT00:00:00Z')
TODAY=$(date -u '+%Y-%m-%dT00:00:00Z')

apploggers telemetry query \
  --source logs \
  --severity error \
  --from "$YESTERDAY" \
  --to "$TODAY" \
  --aggregate severity \
  --output json > /tmp/daily-errors.json

jq '.rows | length' /tmp/daily-errors.json
```

### Monitoreo de Sesión de Usuario

```bash
apploggers telemetry query \
  --source logs \
  --session-id "$SESSION_ID" \
  --aggregate tag \
  --limit 100 \
  --output json | jq '.summary.buckets'
```

### Health Check Automático (Cron)

```bash
#!/bin/bash
if ! apploggers health --output json | jq -e '.ok' > /dev/null; then
  echo "ERROR: AppLoggers health check failed" | mail -s "AppLoggers Down" ops@company.com
  exit 1
fi

ERROR_COUNT=$(apploggers telemetry query \
  --source logs \
  --severity error \
  --from "$(date -u -d '-24 hours' '+%Y-%m-%dT%H:%M:%SZ')" \
  --output json | jq '.count')

if [ "$ERROR_COUNT" -gt 1000 ]; then
  echo "WARNING: $ERROR_COUNT errors in last 24h" | mail -s "High Error Rate" ops@company.com
fi
```

---

## Integración con Agentes

Ver [SKILL.md](../agents/applogger-cli-agent-operator/SKILL.md) para operación de agentes IA.

---

## Variables de Entorno

### Variables primarias

Estas son las variables que el CLI lee directamente. Se usan cuando no hay archivo `~/.apploggers/cli.json` configurado.

| Variable | Requerida | Default | Propósito |
|---|:---:|---|---|
| `appLogger_supabaseUrl` | ✅ | — | URL del proyecto Supabase |
| `appLogger_supabaseKey` | ✅ | — | API key `service_role` (solo backend/operaciones) |
| `appLogger_supabaseSchema` | ❌ | `public` | Esquema PostgreSQL |
| `appLogger_supabaseLogTable` | ❌ | `app_logs` | Nombre de la tabla de logs |
| `appLogger_supabaseMetricTable` | ❌ | `app_metrics` | Nombre de la tabla de métricas |
| `appLogger_supabaseTimeoutSeconds` | ❌ | `15` | Timeout HTTP en segundos (1-120) |

### Variables de control de proyecto

| Variable | Propósito |
|---|---|
| `APPLOGGER_CONFIG` | Ruta al archivo JSON de proyectos (override de `~/.apploggers/cli.json`) |
| `APPLOGGER_PROJECT` | Nombre del proyecto activo |

### Aliases de compatibilidad

El CLI acepta estas variables como fallback en el mismo orden:

| Alias | Variable primaria equivalente |
|---|---|
| `APPLOGGER_SUPABASE_URL` | `appLogger_supabaseUrl` |
| `APPLOGGER_SUPABASE_KEY` | `appLogger_supabaseKey` |
| `APPLOGGER_SUPABASE_SCHEMA` | `appLogger_supabaseSchema` |
| `APPLOGGER_SUPABASE_LOG_TABLE` | `appLogger_supabaseLogTable` |
| `APPLOGGER_SUPABASE_METRIC_TABLE` | `appLogger_supabaseMetricTable` |
| `APPLOGGER_SUPABASE_TIMEOUT_SECONDS` | `appLogger_supabaseTimeoutSeconds` |
| `SUPABASE_URL` | `appLogger_supabaseUrl` |
| `SUPABASE_KEY` | `appLogger_supabaseKey` |

> El CLI requiere `service_role` para consultas `SELECT` con RLS activa. No uses anon/publishable key en `appLogger_supabaseKey`.

---

## Códigos de Salida

| Código | Significado |
|---|---|
| `0` | Éxito |
| `1` | Error en runtime (red caída, Supabase no disponible) |
| `2` | Error de uso/sintaxis (flag inválido, valor fuera de rango) |

---

## Referencia

### Agregaciones Disponibles

| Modo | Fuente | Agrupa por |
|---|---|---|
| `none` | logs, metrics | Sin agrupación — devuelve filas individuales |
| `hour` | logs, metrics | Hora (`created_at`) |
| `severity` | logs | Nivel (`level`) |
| `tag` | logs | Tag (`tag`) |
| `session` | logs, metrics | Sesión (`session_id`) |
| `name` | metrics | Nombre de métrica (`name`) |

### Formato de Timestamp

Los filtros `--from` y `--to` usan RFC3339 (ISO 8601):

```
2026-01-01T10:30:45Z        ← UTC exacto
2026-01-01T10:30:45+00:00   ← UTC con offset
2026-01-01T10:30:45-05:00   ← Hora local (convertida a UTC internamente)
```

```bash
# Fecha actual en RFC3339
date -u '+%Y-%m-%dT%H:%M:%SZ'

# Hace 24 horas (Linux)
date -u -d '-24 hours' '+%Y-%m-%dT%H:%M:%SZ'

# Hace 24 horas (macOS)
date -u -v-24H '+%Y-%m-%dT%H:%M:%SZ'
```

---

## Siguiente Paso

- Instalación detallada: [INSTALLATION.md](./INSTALLATION.md)
- Configuración Supabase corporativa: [SUPABASE_CONFIGURATION.md](./SUPABASE_CONFIGURATION.md)
- Operación con agentes IA: [SKILL.md](../agents/applogger-cli-agent-operator/SKILL.md)

---

→ [GitHub Issues](https://github.com/zuccadev-labs/appLoggers/issues)
→ [Discussions](https://github.com/zuccadev-labs/appLoggers/discussions)
