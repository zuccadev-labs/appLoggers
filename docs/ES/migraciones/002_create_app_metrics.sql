-- Migration: 002_create_app_metrics.sql
-- Descripción: Tabla de métricas de performance y uso

CREATE TABLE IF NOT EXISTS app_metrics (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    name            VARCHAR(100)    NOT NULL,
    value           DOUBLE PRECISION NOT NULL,
    unit            VARCHAR(20)     NOT NULL DEFAULT 'count',
    tags            JSONB           NOT NULL DEFAULT '{}',
    device_id       TEXT            NOT NULL DEFAULT '',
    session_id      TEXT            NOT NULL,
    sdk_version     VARCHAR(20)     NOT NULL DEFAULT '0.0.0'
);

-- Índices para los filtros más frecuentes del CLI y queries analíticas
CREATE INDEX IF NOT EXISTS idx_app_metrics_created_at ON app_metrics (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_metrics_name       ON app_metrics (name);
CREATE INDEX IF NOT EXISTS idx_app_metrics_session_id ON app_metrics (session_id);
CREATE INDEX IF NOT EXISTS idx_app_metrics_device_id  ON app_metrics (device_id);

COMMENT ON TABLE  app_metrics           IS 'Métricas de performance y uso capturadas por AppLogger. Recibe solo eventos LogLevel.METRIC — nunca logs de texto.';
COMMENT ON COLUMN app_metrics.tags      IS 'Tags de contexto garantizados por el SDK: platform, app_version, device_model. Tags adicionales opcionales según lo que pase el desarrollador.';
COMMENT ON COLUMN app_metrics.device_id IS 'Identificador del dispositivo. String opaco generado por el SDK (no necesariamente UUID)';
