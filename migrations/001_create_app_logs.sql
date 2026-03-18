-- Migration: 001_create_app_logs.sql
-- Descripción: Tabla principal de telemetría técnica de AppLogger
-- Motor: PostgreSQL 15+ / Supabase

CREATE TABLE IF NOT EXISTS app_logs (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    level           VARCHAR(10)     NOT NULL
                    CHECK (level IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL', 'METRIC')),
    tag             VARCHAR(100)    NOT NULL DEFAULT '',
    message         TEXT            NOT NULL,
    throwable_type  VARCHAR(200)    NULL,
    throwable_msg   TEXT            NULL,
    stack_trace     TEXT[]          NULL,
    device_info     JSONB           NOT NULL DEFAULT '{}',
    api_level       INTEGER         NOT NULL DEFAULT 0,
    sdk_version     VARCHAR(20)     NOT NULL DEFAULT '0.0.0',
    session_id      UUID            NOT NULL,
    user_id         UUID            NULL,
    extra           JSONB           NULL
);

COMMENT ON TABLE  app_logs              IS 'Eventos de telemetría técnica generados por el SDK AppLogger';
COMMENT ON COLUMN app_logs.device_info  IS 'Metadatos técnicos del dispositivo: brand, model, os_version, api_level, platform, app_version, connection_type, is_tv, is_low_ram';
COMMENT ON COLUMN app_logs.api_level    IS 'Nivel API normalizado. Android usa Build.VERSION.SDK_INT; iOS/JVM almacenan 0';
COMMENT ON COLUMN app_logs.user_id      IS 'UUID anónimo. NULL por defecto. Solo se popula con consentimiento explícito del usuario final';
COMMENT ON COLUMN app_logs.stack_trace  IS 'Array de líneas del stack trace. TV: máx 5 líneas. Mobile: máx 50 líneas';
