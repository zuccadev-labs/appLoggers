-- Migration: 001_create_app_logs.sql
-- Descripción: Tabla principal de telemetría técnica de AppLogger
-- Motor: PostgreSQL 15+ / Supabase

CREATE TABLE IF NOT EXISTS app_logs (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    level           VARCHAR(10)     NOT NULL
                    CHECK (level IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL')),
    tag             VARCHAR(100)    NOT NULL DEFAULT '',
    message         TEXT            NOT NULL,
    throwable_type  VARCHAR(200)    NULL,
    throwable_msg   TEXT            NULL,
    stack_trace     TEXT[]          NULL,
    device_info     JSONB           NOT NULL DEFAULT '{}',
    api_level       INTEGER         NOT NULL DEFAULT 0,
    sdk_version     VARCHAR(20)     NOT NULL DEFAULT '0.0.0',
    session_id      TEXT            NOT NULL,
    device_id       TEXT            NOT NULL DEFAULT '',
    user_id         TEXT            NULL,
    extra           JSONB           NULL
);

-- Índices para los filtros más frecuentes del CLI y queries analíticas
CREATE INDEX IF NOT EXISTS idx_app_logs_created_at  ON app_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_logs_level       ON app_logs (level);
CREATE INDEX IF NOT EXISTS idx_app_logs_session_id  ON app_logs (session_id);
CREATE INDEX IF NOT EXISTS idx_app_logs_device_id   ON app_logs (device_id);
CREATE INDEX IF NOT EXISTS idx_app_logs_tag         ON app_logs (tag);

COMMENT ON TABLE  app_logs              IS 'Eventos de telemetría técnica generados por el SDK AppLogger. Los eventos METRIC van a app_metrics, nunca a esta tabla.';
COMMENT ON COLUMN app_logs.level        IS 'Severidad del evento. METRIC excluido — esos eventos van a app_metrics.';
COMMENT ON COLUMN app_logs.device_info  IS 'Metadatos técnicos del dispositivo: brand, model, os_version, api_level, platform, app_version, connection_type, is_tv, is_low_ram';
COMMENT ON COLUMN app_logs.api_level    IS 'Nivel API normalizado. Android usa Build.VERSION.SDK_INT; iOS/JVM almacenan 0';
COMMENT ON COLUMN app_logs.device_id    IS 'Identificador del dispositivo. String opaco generado por el SDK (no necesariamente UUID)';
COMMENT ON COLUMN app_logs.user_id      IS 'UUID anónimo. NULL por defecto. Solo se popula con consentimiento explícito del usuario final';
COMMENT ON COLUMN app_logs.stack_trace  IS 'Array de líneas del stack trace. TV: máx 5 líneas. Mobile: máx 50 líneas';
COMMENT ON COLUMN app_logs.extra        IS 'Metadatos adicionales en JSONB. Campos conocidos: package_name, error_code, anomaly_type';
