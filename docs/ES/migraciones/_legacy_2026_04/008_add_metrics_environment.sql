-- Migration: 008_add_metrics_environment.sql
-- Descripción: Agrega columna environment top-level en app_metrics
--
-- CONTEXTO:
--   El SDK escribe `environment` como campo top-level en SupabaseMetricEntry.
--   La migración 002 no incluía esta columna, causando que los INSERTs del SDK
--   fallaran con HTTP 400 si Supabase tiene strict mode, o que el campo se
--   ignorara silenciosamente.
--
--   La columna `tags` ya existe desde la migración 002 — el SDK la escribe
--   como `SupabaseMetricEntry.tags`. El CLI 0.2.0 la consulta como `tags`.
--   No se renombra ni se duplica.
--
-- NOTA sobre anomaly_type en app_logs:
--   El SDK 0.2.0 NO escribe anomaly_type como columna top-level.
--   La migración 007 la agrega como columna nullable para uso futuro (SDK 0.3.0+).
--   Hasta entonces, el CLI puede filtrar por ella pero los resultados serán NULL.

-- ── app_metrics: columna environment ─────────────────────────────────────

ALTER TABLE app_metrics
    ADD COLUMN IF NOT EXISTS environment VARCHAR(50) NULL;

COMMENT ON COLUMN app_metrics.environment IS 'Entorno de ejecución: production, staging, development. Enviado por el SDK desde AppLoggerConfig.environment.';

-- ── Índices para los nuevos filtros del CLI ───────────────────────────────

CREATE INDEX IF NOT EXISTS idx_app_metrics_environment
    ON app_metrics (environment)
    WHERE environment IS NOT NULL;

-- Índice compuesto: environment + name (patrón frecuente: métricas por entorno)
CREATE INDEX IF NOT EXISTS idx_app_metrics_env_name
    ON app_metrics (environment, name, created_at DESC)
    WHERE environment IS NOT NULL;
