-- Migration: 003_create_indexes.sql
-- Descripción: Índices para consultas de diagnóstico frecuentes

-- Índice compuesto: filtrar por nivel y rango de tiempo
CREATE INDEX IF NOT EXISTS idx_app_logs_level_created
    ON app_logs (level, created_at DESC);

-- Índice para búsqueda por sesión (diagnóstico de un beta tester)
CREATE INDEX IF NOT EXISTS idx_app_logs_session
    ON app_logs (session_id, created_at DESC);

-- Índice JSONB: filtrar por plataforma dentro de device_info
CREATE INDEX IF NOT EXISTS idx_app_logs_platform
    ON app_logs ((device_info->>'platform'));

-- Índice para métricas por nombre
CREATE INDEX IF NOT EXISTS idx_app_metrics_name_created
    ON app_metrics (name, created_at DESC);

-- Índice para métricas por sesión
CREATE INDEX IF NOT EXISTS idx_app_metrics_session
    ON app_metrics (session_id, created_at DESC);

-- Índice para filtrado por tag en logs (CLI --tag filter, previene full table scan)
CREATE INDEX IF NOT EXISTS idx_app_logs_tag
    ON app_logs (tag, created_at DESC);
