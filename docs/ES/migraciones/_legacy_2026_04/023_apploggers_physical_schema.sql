-- Migration: 023_apploggers_physical_schema.sql
-- Descripcion: Convierte `apploggers` en el schema fisico operativo de AppLoggers
-- consolidando tablas, funciones, triggers y vistas dentro de apploggers.

CREATE SCHEMA IF NOT EXISTS apploggers;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_views WHERE schemaname = 'apploggers' AND viewname = 'app_logs') THEN
        DROP VIEW apploggers.app_logs;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_views WHERE schemaname = 'apploggers' AND viewname = 'app_metrics') THEN
        DROP VIEW apploggers.app_metrics;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_views WHERE schemaname = 'apploggers' AND viewname = 'log_batches') THEN
        DROP VIEW apploggers.log_batches;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_views WHERE schemaname = 'apploggers' AND viewname = 'metric_batches') THEN
        DROP VIEW apploggers.metric_batches;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_views WHERE schemaname = 'apploggers' AND viewname = 'device_remote_config') THEN
        DROP VIEW apploggers.device_remote_config;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_views WHERE schemaname = 'apploggers' AND viewname = 'beta_tester_devices') THEN
        DROP VIEW apploggers.beta_tester_devices;
    END IF;
END $$;

DROP FUNCTION IF EXISTS apploggers.ingest_log_batch(JSONB, JSONB);
DROP FUNCTION IF EXISTS apploggers.ingest_metric_batch(JSONB, JSONB);
DROP FUNCTION IF EXISTS apploggers.purge_old_logs(INTEGER);

CREATE OR REPLACE FUNCTION apploggers.purge_old_logs(retention_days INTEGER DEFAULT 30)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = apploggers, pg_catalog
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM app_logs
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    DELETE FROM app_metrics
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;

    RETURN deleted_count;
END;
$$;

CREATE OR REPLACE FUNCTION apploggers.update_device_config_timestamp()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = apploggers, pg_catalog
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION apploggers.correlate_beta_tester_email()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = apploggers, pg_catalog
AS $$
DECLARE
    is_beta BOOLEAN;
    has_email BOOLEAN;
    tester_email TEXT;
    lookup_email TEXT;
BEGIN
    is_beta := (NEW.extra->>'is_beta_tester') = 'true';
    IF NOT is_beta THEN
        RETURN NEW;
    END IF;

    tester_email := NEW.extra->>'beta_tester_email';
    has_email := tester_email IS NOT NULL AND tester_email != '';

    IF has_email THEN
        INSERT INTO beta_tester_devices (device_id, email, app_package, updated_at)
        VALUES (
            NEW.device_id,
            tester_email,
            COALESCE(NEW.app_package, NEW.extra->>'app_package'),
            NOW()
        )
        ON CONFLICT (device_id) DO UPDATE
        SET email = EXCLUDED.email,
            app_package = EXCLUDED.app_package,
            updated_at = NOW();
    ELSE
        SELECT email INTO lookup_email
        FROM beta_tester_devices
        WHERE device_id = NEW.device_id;

        IF lookup_email IS NOT NULL THEN
            NEW.extra := jsonb_set(
                COALESCE(NEW.extra, '{}'::jsonb),
                '{beta_tester_email}',
                to_jsonb(lookup_email)
            );
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION apploggers.expire_beta_tester_mappings()
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = apploggers, pg_catalog
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM beta_tester_devices
    WHERE updated_at < NOW() - INTERVAL '90 days';
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$;

CREATE OR REPLACE FUNCTION apploggers.ingest_log_batch(
    log_entries JSONB,
    manifest JSONB DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = apploggers, pg_catalog
AS $$
DECLARE
    manifest_batch_id UUID;
    inserted_count INT := 0;
BEGIN
    IF log_entries IS NULL OR jsonb_typeof(log_entries) <> 'array' OR jsonb_array_length(log_entries) = 0 THEN
        RAISE EXCEPTION 'log_entries must be a non-empty JSON array';
    END IF;

    IF manifest IS NOT NULL THEN
        manifest_batch_id := NULLIF(TRIM(manifest->>'batch_id'), '')::UUID;
        IF manifest_batch_id IS NULL THEN
            RAISE EXCEPTION 'manifest.batch_id is required when manifest is provided';
        END IF;
    END IF;

    INSERT INTO apploggers.app_logs (
        id,
        level,
        tag,
        message,
        environment,
        throwable_type,
        throwable_msg,
        stack_trace,
        device_info,
        api_level,
        sdk_version,
        session_id,
        device_id,
        user_id,
        extra,
        anomaly_type,
        trace_id,
        variant,
        batch_id,
        timestamp,
        app_package,
        source_scope,
        source_file,
        source_method
    )
    SELECT
        COALESCE(NULLIF(entry->>'id', '')::UUID, gen_random_uuid()),
        entry->>'level',
        COALESCE(entry->>'tag', ''),
        COALESCE(entry->>'message', ''),
        NULLIF(entry->>'environment', ''),
        NULLIF(entry->>'throwable_type', ''),
        NULLIF(entry->>'throwable_msg', ''),
        CASE
            WHEN entry ? 'stack_trace' AND jsonb_typeof(entry->'stack_trace') = 'array'
                THEN ARRAY(SELECT jsonb_array_elements_text(entry->'stack_trace'))
            ELSE NULL
        END,
        COALESCE(entry->'device_info', '{}'::JSONB),
        COALESCE(NULLIF(entry->>'api_level', '')::INT, 0),
        COALESCE(entry->>'sdk_version', ''),
        COALESCE(entry->>'session_id', ''),
        COALESCE(entry->>'device_id', ''),
        NULLIF(entry->>'user_id', ''),
        CASE
            WHEN entry ? 'extra' AND jsonb_typeof(entry->'extra') = 'object' THEN entry->'extra'
            ELSE NULL
        END,
        NULLIF(entry->>'anomaly_type', ''),
        NULLIF(entry->>'trace_id', ''),
        NULLIF(entry->>'variant', ''),
        CASE
            WHEN NULLIF(entry->>'batch_id', '') IS NULL THEN NULL
            ELSE (entry->>'batch_id')::UUID
        END,
        NULLIF(entry->>'timestamp', '')::BIGINT,
        NULLIF(entry->>'app_package', ''),
        NULLIF(entry->>'source_scope', ''),
        NULLIF(entry->>'source_file', ''),
        NULLIF(entry->>'source_method', '')
    FROM jsonb_array_elements(log_entries) AS entry;

    GET DIAGNOSTICS inserted_count = ROW_COUNT;

    IF manifest IS NOT NULL THEN
        INSERT INTO apploggers.log_batches (
            batch_id,
            event_count,
            batch_hash,
            environment,
            sdk_version,
            key_id
        ) VALUES (
            manifest_batch_id,
            COALESCE(NULLIF(manifest->>'event_count', '')::INT, inserted_count),
            COALESCE(manifest->>'batch_hash', ''),
            NULLIF(manifest->>'environment', ''),
            NULLIF(manifest->>'sdk_version', ''),
            NULLIF(manifest->>'key_id', '')
        )
        ON CONFLICT (batch_id) DO UPDATE SET
            event_count = EXCLUDED.event_count,
            batch_hash = EXCLUDED.batch_hash,
            environment = EXCLUDED.environment,
            sdk_version = EXCLUDED.sdk_version,
            key_id = EXCLUDED.key_id,
            sent_at = NOW();
    END IF;

    RETURN jsonb_build_object(
        'inserted_count', inserted_count,
        'batch_id', COALESCE(manifest->>'batch_id', '')
    );
END;
$$;

CREATE OR REPLACE FUNCTION apploggers.ingest_metric_batch(
    metric_entries JSONB,
    manifest JSONB DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = apploggers, pg_catalog
AS $$
DECLARE
    manifest_batch_id UUID;
    inserted_count INT := 0;
BEGIN
    IF metric_entries IS NULL OR jsonb_typeof(metric_entries) <> 'array' OR jsonb_array_length(metric_entries) = 0 THEN
        RAISE EXCEPTION 'metric_entries must be a non-empty JSON array';
    END IF;

    IF manifest IS NOT NULL THEN
        manifest_batch_id := NULLIF(TRIM(manifest->>'batch_id'), '')::UUID;
        IF manifest_batch_id IS NULL THEN
            RAISE EXCEPTION 'manifest.batch_id is required when manifest is provided';
        END IF;
    END IF;

    INSERT INTO apploggers.app_metrics (
        id,
        name,
        value,
        unit,
        tags,
        environment,
        device_id,
        session_id,
        sdk_version,
        user_id,
        timestamp,
        batch_id,
        app_package,
        source_scope,
        source_file,
        source_method
    )
    SELECT
        COALESCE(NULLIF(entry->>'id', '')::UUID, gen_random_uuid()),
        COALESCE(entry->>'name', ''),
        COALESCE(NULLIF(entry->>'value', '')::DOUBLE PRECISION, 0),
        COALESCE(NULLIF(entry->>'unit', ''), 'count'),
        CASE
            WHEN entry ? 'tags' AND jsonb_typeof(entry->'tags') = 'object' THEN entry->'tags'
            ELSE '{}'::JSONB
        END,
        NULLIF(entry->>'environment', ''),
        COALESCE(entry->>'device_id', ''),
        COALESCE(entry->>'session_id', ''),
        COALESCE(entry->>'sdk_version', ''),
        NULLIF(entry->>'user_id', ''),
        NULLIF(entry->>'timestamp', '')::BIGINT,
        CASE
            WHEN NULLIF(entry->>'batch_id', '') IS NULL THEN NULL
            ELSE (entry->>'batch_id')::UUID
        END,
        NULLIF(entry->>'app_package', ''),
        NULLIF(entry->>'source_scope', ''),
        NULLIF(entry->>'source_file', ''),
        NULLIF(entry->>'source_method', '')
    FROM jsonb_array_elements(metric_entries) AS entry;

    GET DIAGNOSTICS inserted_count = ROW_COUNT;

    IF manifest IS NOT NULL THEN
        INSERT INTO apploggers.metric_batches (
            batch_id,
            event_count,
            batch_hash,
            environment,
            sdk_version,
            key_id
        ) VALUES (
            manifest_batch_id,
            COALESCE(NULLIF(manifest->>'event_count', '')::INT, inserted_count),
            COALESCE(manifest->>'batch_hash', ''),
            NULLIF(manifest->>'environment', ''),
            NULLIF(manifest->>'sdk_version', ''),
            NULLIF(manifest->>'key_id', '')
        )
        ON CONFLICT (batch_id) DO UPDATE SET
            event_count = EXCLUDED.event_count,
            batch_hash = EXCLUDED.batch_hash,
            environment = EXCLUDED.environment,
            sdk_version = EXCLUDED.sdk_version,
            key_id = EXCLUDED.key_id,
            sent_at = NOW();
    END IF;

    RETURN jsonb_build_object(
        'inserted_count', inserted_count,
        'batch_id', COALESCE(manifest->>'batch_id', '')
    );
END;
$$;

CREATE OR REPLACE VIEW apploggers.session_summary AS
SELECT
    session_id,
    device_id,
    user_id,
    environment,
    COUNT(*) AS event_count,
    COUNT(*) FILTER (WHERE level = 'ERROR') AS error_count,
    COUNT(*) FILTER (WHERE level = 'CRITICAL') AS critical_count,
    MIN(created_at) AS first_event,
    MAX(created_at) AS last_event,
    MAX(created_at) - MIN(created_at) AS session_duration
FROM apploggers.app_logs
GROUP BY session_id, device_id, user_id, environment;

CREATE OR REPLACE VIEW apploggers.hourly_error_rate AS
SELECT
    DATE_TRUNC('hour', created_at) AS hour,
    environment,
    COUNT(*) AS total_events,
    COUNT(*) FILTER (WHERE level IN ('ERROR', 'CRITICAL')) AS error_events,
    ROUND(
        COUNT(*) FILTER (WHERE level IN ('ERROR', 'CRITICAL'))::numeric
        / NULLIF(COUNT(*), 0) * 100,
        2
    ) AS error_rate_pct
FROM apploggers.app_logs
GROUP BY DATE_TRUNC('hour', created_at), environment;

CREATE OR REPLACE VIEW apploggers.device_health AS
SELECT
    device_info->>'model' AS device_model,
    device_info->>'os_version' AS os_version,
    environment,
    COUNT(*) AS total_events,
    COUNT(*) FILTER (WHERE level IN ('ERROR', 'CRITICAL')) AS error_events,
    COUNT(DISTINCT session_id) AS session_count
FROM apploggers.app_logs
GROUP BY device_info->>'model', device_info->>'os_version', environment;

CREATE OR REPLACE VIEW apploggers.hourly_metrics_summary AS
SELECT
    DATE_TRUNC('hour', created_at) AS hour,
    name,
    environment,
    COUNT(*) AS sample_count,
    ROUND(AVG(value)::numeric, 4) AS avg_value,
    MIN(value) AS min_value,
    MAX(value) AS max_value,
    ROUND(STDDEV(value)::numeric, 4) AS stddev_value,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY value) AS p95_value,
    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY value) AS p99_value
FROM apploggers.app_metrics
GROUP BY DATE_TRUNC('hour', created_at), name, environment;

DROP VIEW IF EXISTS apploggers.session_summary;
DROP VIEW IF EXISTS apploggers.hourly_error_rate;
DROP VIEW IF EXISTS apploggers.device_health;
DROP VIEW IF EXISTS apploggers.hourly_metrics_summary;

ALTER TABLE apploggers.app_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.app_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.log_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.metric_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.device_remote_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.beta_tester_devices ENABLE ROW LEVEL SECURITY;

COMMENT ON SCHEMA apploggers IS
'Schema fisico y operativo de AppLoggers. Todas las tablas, funciones y vistas operativas viven aqui a partir de la migracion 023.';

GRANT USAGE ON SCHEMA apploggers TO anon, authenticated, service_role;
GRANT INSERT ON apploggers.app_logs TO anon;
GRANT INSERT ON apploggers.app_metrics TO anon;
GRANT INSERT ON apploggers.log_batches TO anon;
GRANT INSERT ON apploggers.metric_batches TO anon;
GRANT SELECT ON apploggers.device_remote_config TO anon;
GRANT SELECT, INSERT, UPDATE ON apploggers.beta_tester_devices TO anon;
GRANT ALL ON ALL TABLES IN SCHEMA apploggers TO service_role;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA apploggers TO anon, service_role;
GRANT SELECT ON apploggers.session_summary TO service_role;
GRANT SELECT ON apploggers.hourly_error_rate TO service_role;
GRANT SELECT ON apploggers.device_health TO service_role;
GRANT SELECT ON apploggers.hourly_metrics_summary TO service_role;

DO $$
DECLARE
    existing_job_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        SELECT jobid
        INTO existing_job_id
        FROM cron.job
        WHERE jobname = 'purge-old-logs-every-3-days'
        LIMIT 1;

        IF existing_job_id IS NOT NULL THEN
            PERFORM cron.unschedule(existing_job_id);
        END IF;

        PERFORM cron.schedule(
            'purge-old-logs-every-3-days',
            '0 3 */3 * *',
            'select apploggers.purge_old_logs(3);'
        );
    END IF;
END;
$$;