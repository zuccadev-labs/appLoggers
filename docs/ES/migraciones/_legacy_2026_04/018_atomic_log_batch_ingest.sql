-- Migration 018: atomic log batch ingest + relational batch integrity guardrails
-- All operations isolated within apploggers schema

CREATE SCHEMA IF NOT EXISTS apploggers;
SET search_path = apploggers, pg_catalog;

CREATE OR REPLACE FUNCTION apploggers.ingest_log_batch(
    log_entries JSONB,
    manifest JSONB DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = apploggers
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
        timestamp
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
        NULLIF(entry->>'timestamp', '')::BIGINT
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

GRANT EXECUTE ON FUNCTION apploggers.ingest_log_batch(JSONB, JSONB) TO anon, service_role;