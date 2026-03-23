# AppLoggers CLI — Guía de Uso y Referencia

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
- [Variables de Control](#variables-de-control)
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

### `~/.apploggers/cli.json` — fuente 

El CLI crea `~/.apploggers/cli.json` automáticamente en el primer run con un template completo. Este archivo es la única fuente de configuración para uso local, agentes IA y SSE. No hay nada más que configurar.

Editar ese archivo es todo lo que se necesita.

```
Windows : C:\Users\<usuario>\.apploggers\cli.json
Linux   : /home/<usuario>/.apploggers/cli.json
macOS   : /Users/<usuario>/.apploggers/cli.json
```

---

### Estructura del archivo

```json
{
  "default_project": "my-app",
  "projects": [
    {
      "name": "my-app",
      "display_name": "My Application",
      "workspace_roots": ["D:/workspace/my-app"],
      "supabase": {
        "url": "https://your-project.supabase.co",
        "api_key": "eyJhbGci..."
      }
    }
  ]
}
```

Reemplazar `url` con la URL del proyecto Supabase y `api_key` con el `service_role` key. El archivo no debe versionarse.

---

### Resolución del API key — `api_key` vs `api_key_env`

Dentro de `supabase`, hay dos formas de proveer la credencial. El CLI aplica esta prioridad:

1. **`api_key_env`** — si está definido y la variable de entorno referenciada está exportada y no vacía, se usa ese valor.
2. **`api_key`** — si `api_key_env` no está definido, o la variable está vacía/no exportada, se usa el valor directo.
3. Ambos vacíos → error con mensaje accionable indicando qué configurar.

#### `api_key` — valor directo (recomendado para la mayoría de los casos)

```json
"supabase": {
  "url": "https://your-project.supabase.co",
  "api_key": "eyJhbGci..."
}
```

El key vive en el archivo. El archivo no se versiona. No hay nada más que hacer — el CLI lo lee directamente en cada ejecución, sin importar el contexto (terminal, agente IA, proceso SSE, MCP).

#### `api_key_env` — indirección por variable de entorno (opcional, para quienes prefieren no tener el key en el archivo)

`api_key_env` recibe el **nombre** de la variable de entorno, no el valor del key. El CLI llama a `os.Getenv(api_key_env)` en tiempo de ejecución. La URL siempre va en el json — no existe variable de entorno para la URL en este path.

```json
"supabase": {
  "url": "https://your-project.supabase.co",
  "api_key_env": "APPLOGGER_SUPABASE_KEY"
}
```

Solo el key se exporta como variable de entorno:

```bash
# Linux / macOS
export APPLOGGER_SUPABASE_KEY="eyJhbGci..."

# Windows PowerShell
$env:APPLOGGER_SUPABASE_KEY = "eyJhbGci..."
```

> `api_key_env` debe contener el **nombre** de la variable (ej: `"APPLOGGER_SUPABASE_KEY"`), nunca el JWT directamente. Si se pone el JWT en `api_key_env`, el CLI falla con: `project "X" requires secret env eyJhbGci...`

Si `api_key_env` está definido pero la variable no está exportada o está vacía, el CLI cae automáticamente a `api_key`. Ambos campos pueden coexistir.

---

### Campos del archivo de proyectos

El archivo `cli.json` configura **la conexión al proyecto Supabase** — no los filtros de consulta. Los filtros son siempre flags de línea de comandos.

| Campo | Requerido | Descripción |
|---|:---:|---|
| `default_project` | ✅ | Nombre del proyecto activo cuando no hay otro criterio de selección |
| `projects[].name` | ✅ | Identificador único del proyecto (case-insensitive) |
| `projects[].display_name` | ❌ | Nombre legible, solo informativo |
| `projects[].workspace_roots` | ❌ | Rutas locales para autodetección de proyecto según directorio de trabajo |
| `supabase.url` | ✅ | URL del proyecto Supabase (`https://xxxx.supabase.co`) |
| `supabase.api_key` | ✅* | Valor directo del `service_role` key. No versionar. |
| `supabase.api_key_env` | ✅* | Nombre de la variable de entorno UPPERCASE que contiene el `service_role` key |
| `supabase.schema` | ❌ | Esquema PostgreSQL. Default: `public` |
| `supabase.logs_table` | ❌ | Nombre de la tabla de logs. Default: `app_logs` |
| `supabase.metrics_table` | ❌ | Nombre de la tabla de métricas. Default: `app_metrics` |
| `supabase.timeout_seconds` | ❌ | Timeout HTTP en segundos (1-120). Default: `15` |

`*` Al menos uno de `api_key` o `api_key_env` debe resolver a un valor no vacío.

`schema`, `logs_table` y `metrics_table` solo se especifican si las migraciones usaron nombres distintos a los defaults. En la mayoría de los casos se omiten.

---

### Ejemplo multi-proyecto

```json
{
  "default_project": "klinema",
  "projects": [
    {
      "name": "klinema",
      "display_name": "Klinema Mobile",
      "workspace_roots": ["D:/workspace/klinema-app"],
      "supabase": {
        "url": "https://klinema.supabase.co",
        "api_key": "eyJhbGci..."
      }
    },
    {
      "name": "klinematv",
      "display_name": "Klinema TV",
      "workspace_roots": ["D:/workspace/klinematv"],
      "supabase": {
        "url": "https://klinematv.supabase.co",
        "api_key": "eyJhbGci..."
      }
    }
  ]
}
```

Con `workspace_roots` configurado, el CLI detecta automáticamente el proyecto activo según el directorio de trabajo. No hace falta pasar `--project` en cada comando.

---

### Casos de uso

| Escenario | Configuración |
|---|---|
| **Uso local / desarrollador** | `cli.json` con `api_key` directo |
| **Agente IA** | `cli.json` con `api_key` directo — el agente lee/escribe el archivo sin gestionar env vars del shell |
| **SSE / proceso Go con frontend** | `cli.json` con `api_key` directo — el proceso hereda el archivo del sistema |
| **Multi-proyecto** | `cli.json` con múltiples entradas + `workspace_roots` para autodetección |
| **Preferencia de no tener key en archivo** | `cli.json` con `api_key_env` apuntando a variable UPPERCASE exportada |

Para configuración corporativa completa (migraciones, RLS, hardening), ver [SUPABASE_CONFIGURATION.md](./SUPABASE_CONFIGURATION.md).

---

## Selección de Proyecto

Cuando existe `~/.apploggers/cli.json`, la selección del proyecto activo sigue esta precedencia:

1. `--project <nombre>` (flag de línea de comandos)
2. `APPLOGGER_PROJECT` (variable de entorno)
3. Detección automática por `workspace_roots` — si el directorio actual está dentro de alguna ruta configurada
4. `default_project` en el archivo de configuración
5. Único proyecto configurado (si solo hay uno)

Si ningún criterio aplica y hay múltiples proyectos, el CLI falla con un error indicando los proyectos disponibles.

```bash
# Selección explícita de proyecto
apploggers --project klinema telemetry query --source logs --severity error --output json
```

---

## Comandos Principales

### `apploggers upgrade`

Actualiza el binario a la última release publicada.

```bash
apploggers upgrade
apploggers upgrade --version apploggers-vX.Y.Z   # versión específica
apploggers upgrade --force                         # forzar reinstalación
```

---

### `apploggers version`

```bash
apploggers version
apploggers version --output json
```

---

### `apploggers capabilities`

```bash
apploggers capabilities --output json
```

---

### `apploggers health`

```bash
apploggers health --output json
```

---

### `apploggers agent schema`

```bash
apploggers agent schema --output json
```

---

## Consultas de Telemetría

### Modelo de datos — qué columnas existen

Antes de filtrar, es importante entender la estructura real de las tablas.

**`app_logs`** — columnas top-level:

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador único del evento |
| `created_at` | TIMESTAMPTZ | Timestamp del evento (UTC) |
| `level` | VARCHAR | Severidad: `DEBUG`, `INFO`, `WARN`, `ERROR`, `CRITICAL`, `METRIC` |
| `tag` | VARCHAR | Dominio del evento (ej: `AUTH`, `PAYMENT`, `NETWORK`, `PLAYER`, `BOOT`) |
| `message` | TEXT | Mensaje descriptivo del evento |
| `session_id` | TEXT | Identificador de sesión del usuario |
| `device_id` | TEXT | Identificador del dispositivo (string opaco generado por el SDK) |
| `user_id` | TEXT | Identificador anónimo del usuario (NULL por defecto, solo con consentimiento) |
| `sdk_version` | VARCHAR | Versión del SDK que generó el evento |
| `extra` | JSONB | Campos adicionales de contexto (ver abajo) |

**`app_logs.extra`** — campos JSONB accesibles via filtros:

| Campo en `extra` | Flag del CLI | Descripción |
|---|---|---|
| `extra.package_name` | `--package` | Paquete o módulo que generó el evento (ej: `com.company.billing`) |
| `extra.error_code` | `--error-code` | Código de error de negocio (ej: `E-42`, `AUTH_FAILED`) |
| `extra.anomaly_type` | `--anomaly-type` | Tipo de anomalía detectada (ej: `slow_response`, `memory_leak`) |

**`app_metrics`** — columnas top-level:

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | UUID | Identificador único |
| `created_at` | TIMESTAMPTZ | Timestamp de la métrica (UTC) |
| `name` | VARCHAR | Nombre de la métrica (ej: `response_time_ms`, `frame_drop_count`) |
| `value` | DOUBLE | Valor numérico de la métrica |
| `unit` | VARCHAR | Unidad de medida (ej: `ms`, `count`, `bytes`) |
| `tags` | JSONB | Contexto de la métrica: `platform`, `app_version`, `device_model`, `screen_name` |
| `device_id` | TEXT | Identificador del dispositivo |
| `session_id` | TEXT | Identificador de sesión |
| `sdk_version` | VARCHAR | Versión del SDK |

---

### Tags — convención de valores

El campo `tag` en `app_logs` identifica el dominio del evento. Los valores son UPPERCASE por convención:

| Tag | Dominio |
|---|---|
| `AUTH` | Autenticación y autorización |
| `NETWORK` | Conectividad y requests HTTP |
| `PAYMENT` | Flujos de pago y transacciones |
| `PLAYER` | Reproducción de contenido multimedia |
| `BOOT` | Ciclo de vida de inicio de la app |

El conjunto de tags es finito y estable — los valores dinámicos van en `extra`, no en `tag`.

---

### `apploggers telemetry query`

Todos los filtros son flags de línea de comandos. No se configuran en `cli.json` — el archivo solo define la conexión al proyecto Supabase.

#### Sintaxis completa

```bash
apploggers telemetry query \
  --source <logs|metrics> \
  [--from TIMESTAMP] \
  [--to TIMESTAMP] \
  [--aggregate MODE] \
  [--severity LEVEL] \
  [--tag TAG] \
  [--session-id UUID] \
  [--device-id ID] \
  [--user-id UUID] \
  [--contains TEXT] \
  [--package PACKAGE_NAME] \
  [--error-code CODE] \
  [--anomaly-type TYPE] \
  [--name METRIC_NAME] \
  [--limit N] \
  [--output FORMAT]
```

#### Referencia de flags

| Flag | Fuente | Columna / campo | Valores | Default |
|---|:---:|---|---|---|
| `--source` | ambas | — | `logs`, `metrics` | `logs` |
| `--from` | ambas | `created_at >= valor` | RFC3339 | — |
| `--to` | ambas | `created_at <= valor` | RFC3339 | — |
| `--aggregate` | ambas | — | `none`, `hour`, `severity`, `tag`, `session`, `name` | `none` |
| `--severity` | logs | `level = UPPERCASE(valor)` | `debug`, `info`, `warn`, `error`, `critical`, `metric` | — |
| `--tag` | logs | `tag = valor` (exact match) | texto libre, UPPERCASE por convención | — |
| `--session-id` | ambas | `session_id = valor` | UUID | — |
| `--device-id` | ambas | `device_id = valor` | UUID o string | — |
| `--user-id` | logs | `user_id = valor` | UUID | — |
| `--contains` | logs | `message ilike *valor*` | texto libre (substring) | — |
| `--package` | logs | `extra->>package_name = valor` | nombre de paquete | — |
| `--error-code` | logs | `extra->>error_code = valor` | código de error | — |
| `--anomaly-type` | logs | `extra->>anomaly_type = valor` | tipo de anomalía | — |
| `--name` | metrics | `name = valor` | nombre de métrica | — |
| `--limit` | ambas | — | 1-1000 | `100` |
| `--output` | — | — | `text`, `json`, `agent` | `text` |

#### Modos de agregación

| Modo | Fuente | Agrupa por | Columna |
|---|:---:|---|---|
| `none` | ambas | Sin agrupación — devuelve filas individuales | — |
| `hour` | ambas | Hora truncada UTC | `created_at` |
| `severity` | logs | Nivel de severidad | `level` |
| `tag` | logs | Tag de dominio | `tag` |
| `session` | ambas | Sesión | `session_id` |
| `name` | metrics | Nombre de métrica | `name` |

#### Ejemplos

```bash
# Logs de error recientes
apploggers telemetry query --source logs --severity error --limit 50

# Logs de un dominio específico, agregados por hora
apploggers telemetry query \
  --source logs --tag PAYMENT --aggregate hour \
  --from 2026-01-01T00:00:00Z --to 2026-01-01T23:59:59Z \
  --output json

# Distribución de severidades en las últimas 24h
apploggers telemetry query \
  --source logs --aggregate severity \
  --from 2026-01-01T00:00:00Z --to 2026-01-02T00:00:00Z \
  --output json

# Reconstrucción completa de una sesión
apploggers telemetry query \
  --source logs --session-id <uuid> --limit 1000 --output json

# Métricas de una sesión, agrupadas por nombre
apploggers telemetry query \
  --source metrics --session-id <uuid> --aggregate name --output json

# Logs de un dispositivo específico con anomalías de red
apploggers telemetry query \
  --source logs --device-id <id> --tag NETWORK \
  --anomaly-type slow_response --output json

# Búsqueda por texto en mensajes + código de error
apploggers telemetry query \
  --source logs --contains timeout \
  --error-code E-42 --severity error \
  --package com.company.billing --limit 50 --output json

# Métricas de performance por nombre
apploggers telemetry query \
  --source metrics --name response_time_ms --aggregate name --output json
```

> Los filtros se combinan con AND — todos los flags activos deben cumplirse simultáneamente.
> `--package`, `--error-code` y `--anomaly-type` filtran dentro del campo JSONB `extra`, no son columnas top-level.

---

### Columnas presentes en la DB pero no expuestas como flags

Estas columnas existen en las tablas pero el CLI no tiene flags para filtrarlas directamente. Son accesibles via Supabase Dashboard o queries SQL directas.

**`app_logs` — columnas sin flag CLI:**

| Columna | Tipo | Descripción |
|---|---|---|
| `throwable_type` | VARCHAR | Tipo de excepción (ej: `NullPointerException`) |
| `throwable_msg` | TEXT | Mensaje de la excepción |
| `stack_trace` | TEXT[] | Array de líneas del stack trace |
| `device_info` | JSONB | Metadatos del dispositivo: `brand`, `model`, `os_version`, `platform`, `app_version`, `connection_type`, `is_tv`, `is_low_ram` |
| `api_level` | INTEGER | Nivel API del dispositivo (Android: `Build.VERSION.SDK_INT`; iOS/JVM: `0`) |

**`app_metrics` — campo sin flag CLI:**

| Campo | Tipo | Descripción |
|---|---|---|
| `tags` | JSONB | Contexto de la métrica: `platform`, `app_version`, `device_model`, `screen_name` |

> Filtrar por `tags` de métricas (ej: solo métricas de `platform=android`) requiere query SQL directa en Supabase. No hay flag CLI para ello.

---

### `apploggers telemetry agent-response`

Salida compacta optimizada para agentes IA (formato TOON). Acepta los mismos flags de filtro que `query`, más `--preview-limit`.

```bash
apploggers telemetry agent-response \
  --source logs \
  --aggregate severity \
  --preview-limit 3 \
  --output agent
```

| Parámetro | Default | Propósito |
|---|---|---|
| `--preview-limit` | 5 | Filas de muestra incluidas en `rows_preview` (0..50) |

---

## Modos de Salida

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
  --source logs --severity error \
  --from "$YESTERDAY" --to "$TODAY" \
  --aggregate severity --output json > /tmp/daily-errors.json

jq '.rows | length' /tmp/daily-errors.json
```

### Monitoreo de Sesión de Usuario

```bash
apploggers telemetry query \
  --source logs --session-id "$SESSION_ID" \
  --aggregate tag --limit 100 --output json | jq '.summary.buckets'
```

### Health Check Automático (Cron)

```bash
#!/bin/bash
if ! apploggers health --output json | jq -e '.ok' > /dev/null; then
  echo "ERROR: AppLoggers health check failed" | mail -s "AppLoggers Down" ops@company.com
  exit 1
fi

ERROR_COUNT=$(apploggers telemetry query \
  --source logs --severity error \
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

## Variables de Control

Estas variables controlan el comportamiento del CLI y son independientes de `cli.json`:

| Variable | Propósito |
|---|---|
| `APPLOGGER_CONFIG` | Ruta alternativa al archivo de proyectos (override de `~/.apploggers/cli.json`) |
| `APPLOGGER_PROJECT` | Nombre del proyecto activo (override de `default_project`) |

> Las variables de entorno directas para URL y key (`APPLOGGER_SUPABASE_URL`, `APPLOGGER_SUPABASE_KEY`, etc.) están deprecadas para uso local. Solo se mantienen por compatibilidad hacia atrás y se leen únicamente cuando `~/.apploggers/cli.json` no existe. Configurar siempre a través del archivo.

---

## Códigos de Salida

| Código | Significado |
|---|---|
| `0` | Éxito |
| `1` | Error en runtime (red caída, Supabase no disponible) |
| `2` | Error de uso/sintaxis (flag inválido, valor fuera de rango) |

---

## Referencia

### Formato de Timestamp

Los filtros `--from` y `--to` usan RFC3339 (ISO 8601):

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
