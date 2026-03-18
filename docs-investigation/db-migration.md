# AppLogger — Estándar de Base de Datos y Migraciones

**Versión:** 0.1.1  
**Fecha:** 2026-03-17  
**Motor:** PostgreSQL 15+ / Supabase  
**Scope:** Esquema de almacenamiento para logs de telemetría técnica — Android (Mobile + TV) · iOS · JVM

---

## Índice

1. [Principios de Diseño de la Base de Datos](#1-principios-de-diseño-de-la-base-de-datos)
2. [Esquema Principal — Tabla app_logs](#2-esquema-principal--tabla-app_logs)
3. [Esquema de Métricas — Tabla app_metrics](#3-esquema-de-métricas--tabla-app_metrics)
4. [Índices y Optimización de Consultas](#4-índices-y-optimización-de-consultas)
5. [Row Level Security (RLS) — Supabase](#5-row-level-security-rls--supabase)
6. [Política de Retención y Purga Automática](#6-política-de-retención-y-purga-automática)
7. [Estrategia de Particionado](#7-estrategia-de-particionado)
8. [Script de Migración Versionada](#8-script-de-migración-versionada)
9. [Consultas de Diagnóstico Útiles](#9-consultas-de-diagnóstico-útiles)

---

## 1. Principios de Diseño de la Base de Datos

### 1.1 Optimizado para Escritura

El patrón de uso de `app_logs` es **write-heavy / read-occasional**:
- Miles de inserciones por hora, provenientes de múltiples dispositivos.
- Lecturas puntuales y por rango de tiempo para diagnóstico.
- Nunca se actualizan ni borran registros individuales (append-only).

Esto implica:
- **Índices mínimos en escritura**: solo los índices necesarios para las consultas de diagnóstico más frecuentes.
- **JSONB para device_info**: extensible sin alterar el esquema. Nuevos campos del dispositivo no requieren migraciones.
- **Particionado por tiempo** (en volúmenes altos): facilita el archivado y la purga por `created_at`.

### 1.2 Sin PII en la Base de Datos

El esquema está diseñado para **nunca almacenar información personal identificable**:
- No hay campos de nombre, email, dirección o número de teléfono.
- `user_id` es un UUID anónimo generado por la app (no un ID de usuario real del sistema de autenticación).
- Las IPs de origen **no se persisten** en la tabla (Supabase puede recibirlas en headers de red, pero no se almacenan).

---

## 2. Esquema Principal — Tabla `app_logs`

### 2.1 Migration 001 — Creación inicial

```sql
-- Migration: 001_create_app_logs.sql
-- Descripción: Tabla principal de telemetría técnica de AppLogger
-- Motor: PostgreSQL 15+ / Supabase

CREATE TABLE IF NOT EXISTS app_logs (
    -- Identificador único del evento
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Temporalidad (con zona horaria UTC)
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Nivel de severidad del evento
    level           VARCHAR(10)     NOT NULL
                    CHECK (level IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL', 'METRIC')),

    -- Tag origen del log (ej: "GRPC", "WEBSOCKET", "CRASH", nombre de Activity)
    tag             VARCHAR(100)    NOT NULL DEFAULT '',

    -- Mensaje descriptivo del evento
    message         TEXT            NOT NULL,

    -- Información serializada del error (solo cuando level = ERROR | CRITICAL)
    throwable_type  VARCHAR(200)    NULL,
    throwable_msg   TEXT            NULL,
    stack_trace     TEXT[]          NULL,   -- Array de líneas (limitado por la librería)

    -- Metadatos técnicos del dispositivo (JSONB: extensible sin migración)
    device_info     JSONB           NOT NULL DEFAULT '{}',

    -- API level normalizado para filtros y dashboards (Android: 22+, iOS/JVM: 0)
    api_level       INTEGER         NOT NULL DEFAULT 0,

    -- Versión del paquete AppLogger que generó el evento
    sdk_version     VARCHAR(20)     NOT NULL DEFAULT '0.0.0',

    -- Identificador de sesión (UUID efímero, no correlacionable con usuario real)
    session_id      UUID            NOT NULL,

    -- Identificador anónimo del usuario (solo si fue proporcionado con consentimiento)
    user_id         UUID            NULL,

    -- Metadatos adicionales específicos del contexto (campos extra opcionales)
    extra           JSONB           NULL
);

COMMENT ON TABLE  app_logs              IS 'Eventos de telemetría técnica generados por el SDK AppLogger';
COMMENT ON COLUMN app_logs.device_info  IS 'Metadatos técnicos del dispositivo: brand, model, os_version, api_level, platform, app_version, connection_type, is_tv, is_low_ram';
COMMENT ON COLUMN app_logs.api_level    IS 'Nivel API normalizado. Android usa Build.VERSION.SDK_INT; iOS/JVM almacenan 0';
COMMENT ON COLUMN app_logs.user_id      IS 'UUID anónimo. NULL por defecto. Solo se popula con consentimiento explícito del usuario final';
COMMENT ON COLUMN app_logs.stack_trace  IS 'Array de líneas del stack trace. TV: máx 5 líneas. Mobile: máx 50 líneas';
```

### 2.2 Estructura del campo `device_info` (JSONB)

El campo `device_info` almacena el `DeviceInfo` serializado. Ejemplo de valor real:

```json
{
  "brand": "Google",
  "model": "Pixel 7",
  "os_version": "14",
  "api_level": 34,
  "platform": "android_mobile",
  "app_version": "2.1.0",
  "app_build": 210,
  "is_low_ram": false,
  "is_tv": false,
  "connection_type": "wifi"
}
```

Para Android TV:

```json
{
  "brand": "Philips",
  "model": "PFL6008",
  "os_version": "11",
  "api_level": 30,
  "platform": "android_tv",
  "app_version": "2.1.0",
  "app_build": 210,
  "is_low_ram": true,
  "is_tv": true,
  "connection_type": "ethernet"
}
```

Para iOS (iPhone/iPad):

```json
{
  "brand": "Apple",
  "model": "iPhone 15 Pro",
  "os_version": "17.3",
  "api_level": 0,
  "platform": "ios",
  "app_version": "2.1.0",
  "app_build": 210,
  "is_low_ram": false,
  "is_tv": false,
  "connection_type": "wifi"
}
```

> **Nota sobre `api_level` en iOS:** Se almacena como `0` ya que el concepto de API Level no existe en el ecosistema Apple. La versión del sistema operativo está en `os_version` con suficiente granularidad.

**Valores admitidos del campo `platform`:**

| Valor | Plataforma |
|---|---|
| `android_mobile` | Android smartphone / tablet |
| `android_tv` | Android TV / Google TV |
| `ios` | iPhone / iPad |
| `jvm` | JVM desktop o servidor |

---

## 3. Esquema de Métricas — Tabla `app_metrics`

Para eventos de tipo `METRIC` (performance, contadores de uso), se usa una tabla separada optimizada para agregaciones:

```sql
-- Migration: 002_create_app_metrics.sql
-- Descripción: Tabla de métricas de performance y uso

CREATE TABLE IF NOT EXISTS app_metrics (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Nombre de la métrica (ej: "screen_load_time", "api_response_time")
    name            VARCHAR(100)    NOT NULL,

    -- Valor numérico de la métrica
    value           DOUBLE PRECISION NOT NULL,

    -- Unidad de medida (ms, bytes, count, percent)
    unit            VARCHAR(20)     NOT NULL DEFAULT 'count',

    -- Tags para filtrado (plataforma, versión, etc.)
    tags            JSONB           NOT NULL DEFAULT '{}',

    session_id      UUID            NOT NULL,
    sdk_version     VARCHAR(20)     NOT NULL DEFAULT '0.0.0'
);

COMMENT ON TABLE  app_metrics       IS 'Métricas de performance y uso capturadas por AppLogger';
COMMENT ON COLUMN app_metrics.tags  IS 'Tags de contexto: platform, app_version, device_model, screen_name';
```

---

## 4. Índices y Optimización de Consultas

```sql
-- Migration: 003_create_indexes.sql
-- Índices diseñados para las consultas de diagnóstico más frecuentes

-- Consulta más común: "dame los errores de las últimas N horas"
CREATE INDEX idx_logs_created_at
    ON app_logs (created_at DESC);

-- Filtrar por nivel de severidad (ej: solo CRITICAL y ERROR)
CREATE INDEX idx_logs_level
    ON app_logs (level, created_at DESC);

-- Buscar todos los logs de una sesión específica
CREATE INDEX idx_logs_session_id
    ON app_logs (session_id, created_at DESC);

-- Buscar por plataforma dentro de device_info (JSONB GIN index)
CREATE INDEX idx_logs_device_platform
    ON app_logs USING GIN ((device_info -> 'platform'));

-- Buscar por versión de app (útil para diagnóstico post-release)
CREATE INDEX idx_logs_app_version
    ON app_logs USING GIN ((device_info -> 'app_version'));

-- Filtrado rápido por API level (matriz de compatibilidad)
CREATE INDEX idx_logs_api_level
    ON app_logs (api_level, created_at DESC);

-- Índice parcial: solo eventos críticos (optimiza alertas automáticas)
CREATE INDEX idx_logs_critical_errors
    ON app_logs (created_at DESC)
    WHERE level IN ('ERROR', 'CRITICAL');

-- app_metrics: consultas de agregación por nombre y tiempo
CREATE INDEX idx_metrics_name_time
    ON app_metrics (name, created_at DESC);
```

---

## 5. Row Level Security (RLS) — Supabase

La tabla `app_logs` solo debe permitir **inserciones** desde clientes no autenticados usando la `anon key`. Nunca debe exponer datos a través de la API pública.

```sql
-- Migration: 004_enable_rls.sql

-- Habilitar RLS en ambas tablas
ALTER TABLE app_logs    ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_metrics ENABLE ROW LEVEL SECURITY;

-- Política: el cliente anónimo solo puede INSERT (nunca SELECT, UPDATE o DELETE)
CREATE POLICY "allow_insert_only_app_logs"
    ON app_logs
    FOR INSERT
    TO anon
    WITH CHECK (true);  -- Cualquier inserción es válida (validación en la app)

CREATE POLICY "allow_insert_only_app_metrics"
    ON app_metrics
    FOR INSERT
    TO anon
    WITH CHECK (true);

-- Política: los roles autenticados (dashboard de diagnóstico) pueden SELECT
CREATE POLICY "allow_read_authenticated"
    ON app_logs
    FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "allow_read_metrics_authenticated"
    ON app_metrics
    FOR SELECT
    TO authenticated
    USING (true);

-- IMPORTANTE: Ninguna política permite UPDATE o DELETE desde el cliente
-- La purga de datos se hace solo desde el servidor (service_role) vía cron
```

### 5.1 Resumen de Permisos por Rol

| Rol | INSERT | SELECT | UPDATE | DELETE |
|---|---|---|---|---|
| `anon` (SDK de la app) | ✅ | ❌ | ❌ | ❌ |
| `authenticated` (dashboard) | ❌ | ✅ | ❌ | ❌ |
| `service_role` (cron/admin) | ✅ | ✅ | ✅ | ✅ |

---

## 6. Política de Retención y Purga Automática

Para evitar crecimiento indefinido de la tabla, se implementa una función de purga programada:

```sql
-- Migration: 005_retention_policy.sql

-- Función que borra logs más viejos que N días
CREATE OR REPLACE FUNCTION purge_old_logs(retention_days INTEGER DEFAULT 30)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER  -- Se ejecuta con permisos del propietario (service_role)
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM app_logs
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    -- Registrar la purga en los propios logs de auditoría
    RAISE NOTICE 'AppLogger purge: % rows deleted (retention: % days)', deleted_count, retention_days;

    RETURN deleted_count;
END;
$$;

-- Función equivalente para métricas (retención más corta, 7 días por defecto)
CREATE OR REPLACE FUNCTION purge_old_metrics(retention_days INTEGER DEFAULT 7)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM app_metrics
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$;
```

### 6.1 Programar la Purga (pg_cron en Supabase)

```sql
-- Requiere extensión pg_cron (disponible en Supabase)
-- Ejecutar diariamente a las 3:00 AM UTC

SELECT cron.schedule(
    'purge-app-logs-daily',
    '0 3 * * *',
    'SELECT purge_old_logs(30);'
);

SELECT cron.schedule(
    'purge-app-metrics-daily',
    '0 3 * * *',
    'SELECT purge_old_metrics(7);'
);
```

---

## 7. Estrategia de Particionado

Para proyectos con alto volumen (> 1M logs/día), se recomienda particionado por rango mensual:

```sql
-- Migration: 006_partitioning.sql (solo para volumen alto)
-- Nota: Aplicar ANTES de insertar datos. Requiere recrear la tabla.

CREATE TABLE app_logs_partitioned (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    level           VARCHAR(10)     NOT NULL,
    tag             VARCHAR(100)    NOT NULL DEFAULT '',
    message         TEXT            NOT NULL,
    throwable_type  VARCHAR(200)    NULL,
    throwable_msg   TEXT            NULL,
    stack_trace     TEXT[]          NULL,
    device_info     JSONB           NOT NULL DEFAULT '{}',
    sdk_version     VARCHAR(20)     NOT NULL DEFAULT '0.0.0',
    session_id      UUID            NOT NULL,
    user_id         UUID            NULL,
    extra           JSONB           NULL
) PARTITION BY RANGE (created_at);

-- Crear partición para el mes actual (repetir mensualmente por cron)
CREATE TABLE app_logs_2026_03
    PARTITION OF app_logs_partitioned
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

CREATE TABLE app_logs_2026_04
    PARTITION OF app_logs_partitioned
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
```

---

## 8. Script de Migración Versionada

### 8.1 Convención de nombres

```
migrations/
├── 001_create_app_logs.sql
├── 002_create_app_metrics.sql
├── 003_create_indexes.sql
├── 004_enable_rls.sql
├── 005_retention_policy.sql
└── 006_partitioning.sql   (opcional, volumen alto)
```

### 8.2 Script de aplicación ordenada

```sql
-- run_migrations.sql
-- Ejecutar en orden. Cada migración es idempotente (IF NOT EXISTS).

\ir migrations/001_create_app_logs.sql
\ir migrations/002_create_app_metrics.sql
\ir migrations/003_create_indexes.sql
\ir migrations/004_enable_rls.sql
\ir migrations/005_retention_policy.sql
```

---

## 9. Consultas de Diagnóstico Útiles

```sql
-- 1. Últimos 50 errores críticos
SELECT created_at, tag, message, throwable_type,
       device_info->>'platform'    AS platform,
       device_info->>'app_version' AS app_version,
       device_info->>'model'       AS device_model
FROM app_logs
WHERE level IN ('ERROR', 'CRITICAL')
ORDER BY created_at DESC
LIMIT 50;

-- 2. Errores agrupados por tipo en las últimas 24 horas
SELECT throwable_type, COUNT(*) AS occurrences,
       MIN(created_at) AS first_seen,
       MAX(created_at) AS last_seen
FROM app_logs
WHERE level IN ('ERROR', 'CRITICAL')
  AND created_at > NOW() - INTERVAL '24 hours'
GROUP BY throwable_type
ORDER BY occurrences DESC;

-- 3. Distribución de plataformas entre los beta testers
SELECT device_info->>'platform'    AS platform,
       device_info->>'app_version' AS app_version,
       COUNT(DISTINCT session_id)  AS unique_sessions
FROM app_logs
WHERE created_at > NOW() - INTERVAL '7 days'
GROUP BY platform, app_version
ORDER BY unique_sessions DESC;

-- 4. Anomalías de latencia en gRPC por método
SELECT extra->>'method'     AS grpc_method,
       COUNT(*)              AS anomaly_count,
       AVG((extra->>'duration_ms')::numeric) AS avg_ms
FROM app_logs
WHERE tag = 'GRPC_LATENCY'
  AND created_at > NOW() - INTERVAL '1 day'
GROUP BY grpc_method
ORDER BY anomaly_count DESC;

-- 5. Logs de una sesión específica (para debugging de un reporte)
SELECT created_at, level, tag, message
FROM app_logs
WHERE session_id = 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
ORDER BY created_at ASC;

-- 6. Volumen de logs por hora (para detectar picos anómalos)
SELECT DATE_TRUNC('hour', created_at) AS hour,
       level,
       COUNT(*) AS event_count
FROM app_logs
WHERE created_at > NOW() - INTERVAL '48 hours'
GROUP BY hour, level
ORDER BY hour DESC, level;
```
