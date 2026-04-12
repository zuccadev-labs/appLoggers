-- Migration: 007_add_environment_anomaly_type.sql
-- Descripción: Agrega columnas top-level requeridas por CLI 0.2.0
--
-- CONTEXTO:
--   El CLI 0.2.0 consulta `environment` y `anomaly_type` como columnas
--   top-level en app_logs (no como campos JSONB dentro de `extra`).
--   Esta migración las promueve a columnas de primera clase para permitir
--   filtrado eficiente con índices y queries directas via Supabase REST API.
--
-- RETROCOMPATIBILIDAD:
--   - `extra` sigue existiendo y puede contener otros campos ad-hoc.
--   - Los SDKs que ya escribían anomaly_type dentro de `extra` seguirán
--     funcionando; el CLI 0.2.0 lee la columna top-level.
--   - Si se desea backfill: UPDATE app_logs SET anomaly_type = extra->>'anomaly_type'
--     WHERE anomaly_type IS NULL AND extra->>'anomaly_type' IS NOT NULL;

-- ── app_logs: columnas nuevas ─────────────────────────────────────────────

ALTER TABLE app_logs
    ADD COLUMN IF NOT EXISTS environment   VARCHAR(50)  NULL,
    ADD COLUMN IF NOT EXISTS anomaly_type  VARCHAR(100) NULL;

COMMENT ON COLUMN app_logs.environment  IS 'Entorno de ejecución: production, staging, development. Enviado por el SDK desde local.properties o configuración explícita.';
COMMENT ON COLUMN app_logs.anomaly_type IS 'Clasificación de anomalía top-level: crash, anr, oom, network_timeout, etc. Promovido desde extra para permitir filtrado eficiente.';

-- ── Índices para los nuevos filtros del CLI ───────────────────────────────

CREATE INDEX IF NOT EXISTS idx_app_logs_environment
    ON app_logs (environment)
    WHERE environment IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_app_logs_anomaly_type
    ON app_logs (anomaly_type)
    WHERE anomaly_type IS NOT NULL;

-- Índice compuesto: environment + level (patrón frecuente: errores en producción)
CREATE INDEX IF NOT EXISTS idx_app_logs_env_level
    ON app_logs (environment, level, created_at DESC)
    WHERE environment IS NOT NULL;
