-- Migration 003: AppLoggers operational functions and triggers
-- Scope: ingestion, retention, integrity, and correlation flows.

SET search_path = apploggers, pg_catalog;

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
    tester_email TEXT;
    lookup_email TEXT;
BEGIN
    is_beta := COALESCE(NEW.extra->>'is_beta_tester', 'false') = 'true';
    IF NOT is_beta THEN
        RETURN NEW;
    END IF;

    tester_email := NULLIF(NEW.extra->>'beta_tester_email', '');

    IF tester_email IS NOT NULL THEN
        INSERT INTO apploggers.beta_tester_devices (device_id, email, app_package, updated_at)
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
        FROM apploggers.beta_tester_devices
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
    DELETE FROM apploggers.beta_tester_devices
    WHERE updated_at < NOW() - INTERVAL '90 days';

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$;

CREATE OR REPLACE FUNCTION apploggers.purge_old_logs(retention_days INTEGER DEFAULT 30)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = apploggers, pg_catalog
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM apploggers.app_logs
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    DELETE FROM apploggers.app_metrics
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;

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
        id, level, tag, message, environment, throwable_type, throwable_msg,
        stack_trace, device_info, api_level, sdk_version, session_id, device_id,
        user_id, extra, anomaly_type, trace_id, variant, batch_id, timestamp,
        app_package, source_scope, source_file, source_method
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
            batch_id, event_count, batch_hash, environment, sdk_version, key_id
        ) VALUES (
            manifest_batch_id,
            COALESCE(NULLIF(manifest->>'event_count', '')::INT, inserted_count),
            COALESCE(NULLIF(manifest->>'batch_hash', ''), ''),
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
        'batch_id', COALESCE(manifest->>'batch_id', ''),
        'status', 'success'
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
        id, name, value, unit, tags, environment, device_id, session_id,
        sdk_version, user_id, timestamp, batch_id, app_package, source_scope,
        source_file, source_method
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
            batch_id, event_count, batch_hash, environment, sdk_version, key_id
        ) VALUES (
            manifest_batch_id,
            COALESCE(NULLIF(manifest->>'event_count', '')::INT, inserted_count),
            COALESCE(NULLIF(manifest->>'batch_hash', ''), ''),
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
        'batch_id', COALESCE(manifest->>'batch_id', ''),
        'status', 'success'
    );
END;
$$;

DROP TRIGGER IF EXISTS trg_device_remote_config_updated ON apploggers.device_remote_config;
CREATE TRIGGER trg_device_remote_config_updated
    BEFORE UPDATE ON apploggers.device_remote_config
    FOR EACH ROW
    EXECUTE FUNCTION apploggers.update_device_config_timestamp();

DROP TRIGGER IF EXISTS trg_correlate_beta_tester ON apploggers.app_logs;
CREATE TRIGGER trg_correlate_beta_tester
    BEFORE INSERT ON apploggers.app_logs
    FOR EACH ROW
    EXECUTE FUNCTION apploggers.correlate_beta_tester_email();
