-- Migration: 009_add_missing_indexes.sql
-- Descripción: Índices faltantes para filtros del CLI que causaban full table scans
--
-- CONTEXTO:
--   El CLI filtra por --sdk-version, --user-id y --device-id (metrics) en queries
--   frecuentes de diagnóstico. Sin índices, cada query hace un full table scan.
--   En tablas con millones de eventos esto degrada el tiempo de respuesta de
--   segundos a decenas de segundos.
--
-- IMPACTO:
--   - app_logs.sdk_version:   CLI --sdk-version filtra logs por versión del SDK
--   - app_metrics.sdk_version: CLI --sdk-version filtra métricas por versión del SDK
--   - app_logs.user_id:       CLI --user-id filtra logs por usuario anónimo
--   - app_metrics.device_id:  CLI --device-id filtra métricas por dispositivo
--
-- NOTA: app_logs.device_id ya tiene índice desde migración 001.
--       app_metrics.device_id NO tenía índice — se agrega aquí.

-- ── app_logs: índices faltantes ───────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_app_logs_sdk_version
    ON app_logs (sdk_version)
    WHERE sdk_version IS NOT NULL AND sdk_version != '0.0.0';

CREATE INDEX IF NOT EXISTS idx_app_logs_user_id
    ON app_logs (user_id)
    WHERE user_id IS NOT NULL;

-- ── app_metrics: índices faltantes ───────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_app_metrics_sdk_version
    ON app_metrics (sdk_version)
    WHERE sdk_version IS NOT NULL AND sdk_version != '0.0.0';

-- NOTA: idx_app_metrics_device_id ya existe desde migración 002.
-- No se recrea aquí para evitar duplicados.

COMMENT ON INDEX idx_app_logs_sdk_version    IS 'Soporta CLI --sdk-version en app_logs. Partial index excluye valor default 0.0.0.';
COMMENT ON INDEX idx_app_logs_user_id        IS 'Soporta CLI --user-id en app_logs. Partial index excluye NULLs (mayoría de filas).';
COMMENT ON INDEX idx_app_metrics_sdk_version IS 'Soporta CLI --sdk-version en app_metrics. Partial index excluye valor default 0.0.0.';
