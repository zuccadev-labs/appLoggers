-- Migration: 002_create_app_metrics.sql
-- Descripción: Tabla de métricas de performance y uso

CREATE TABLE IF NOT EXISTS app_metrics (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    name            VARCHAR(100)    NOT NULL,
    value           DOUBLE PRECISION NOT NULL,
    unit            VARCHAR(20)     NOT NULL DEFAULT 'count',
    tags            JSONB           NOT NULL DEFAULT '{}',
    session_id      UUID            NOT NULL,
    sdk_version     VARCHAR(20)     NOT NULL DEFAULT '0.0.0'
);

COMMENT ON TABLE  app_metrics       IS 'Métricas de performance y uso capturadas por AppLogger';
COMMENT ON COLUMN app_metrics.tags  IS 'Tags de contexto: platform, app_version, device_model, screen_name';
