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
    app_package     TEXT            NULL,
    source_scope    TEXT            NULL,
    source_file     TEXT            NULL,
    source_method   TEXT            NULL,
    sdk_version     VARCHAR(20)     NOT NULL DEFAULT '0.0.0'
);

-- Índices para los filtros más frecuentes del CLI y queries analíticas
CREATE INDEX IF NOT EXISTS idx_app_metrics_created_at ON app_metrics (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_metrics_name       ON app_metrics (name);
CREATE INDEX IF NOT EXISTS idx_app_metrics_session_id ON app_metrics (session_id);
CREATE INDEX IF NOT EXISTS idx_app_metrics_device_id  ON app_metrics (device_id);
CREATE INDEX IF NOT EXISTS idx_app_metrics_app_package ON app_metrics (app_package);
CREATE INDEX IF NOT EXISTS idx_app_metrics_source_scope ON app_metrics (source_scope);

COMMENT ON TABLE  app_metrics           IS 'Métricas de performance y uso capturadas por AppLogger. Recibe solo eventos LogLevel.METRIC — nunca logs de texto.';
COMMENT ON COLUMN app_metrics.tags      IS 'Tags de contexto garantizados por el SDK: platform, app_version, device_model. Tags adicionales opcionales según lo que pase el desarrollador.';
COMMENT ON COLUMN app_metrics.device_id IS 'Identificador del dispositivo. String opaco generado por el SDK (no necesariamente UUID)';
COMMENT ON COLUMN app_metrics.app_package IS 'Identidad estable de la app que emitió la métrica (ej: com.methosmedia.klinema).';
COMMENT ON COLUMN app_metrics.source_scope IS 'Origen jerárquico estable de la métrica para filtros corporativos.';
COMMENT ON COLUMN app_metrics.source_file IS 'Archivo fuente opcional capturado por modo forense de caller capture.';
COMMENT ON COLUMN app_metrics.source_method IS 'Método o función opcional capturado por modo forense de caller capture.';
