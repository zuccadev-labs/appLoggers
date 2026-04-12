-- Migration 019: versioned integrity keys + separated metric batch verification
-- CORPORATE FORENSIC STANDARD: All new tables and functions created IN apploggers schema
-- Single-schema model: all objects and operations remain in apploggers
-- 
-- Architecture:
--   - apploggers.log_batches: NEW table for log batch tracking (with key_id column)
--   - apploggers.metric_batches: NEW table for metric batch tracking  
--   - apploggers.ingest_log_batch(): NEW function for atomic log batch ingestion
--   - apploggers.ingest_metric_batch(): NEW function for atomic metric batch ingestion

CREATE SCHEMA IF NOT EXISTS apploggers;

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 1: Create log_batches table IN apploggers schema
-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS apploggers.log_batches (
    batch_id UUID PRIMARY KEY,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_count INT NOT NULL CHECK (event_count > 0),
    batch_hash TEXT NOT NULL DEFAULT '',
    environment VARCHAR(50) NULL
        CHECK (environment IS NULL OR environment IN ('development', 'staging', 'production', 'test')),
    sdk_version VARCHAR(20) NULL,
    key_id VARCHAR(64) NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT log_batches_batch_hash_min_length CHECK (CHAR_LENGTH(batch_hash) > 0)
);

CREATE INDEX IF NOT EXISTS idx_apploggers_log_batches_sent_at
    ON apploggers.log_batches (sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_log_batches_key_id
    ON apploggers.log_batches (key_id);

ALTER TABLE apploggers.log_batches ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'apploggers' AND tablename = 'log_batches' AND policyname = 'log_batch_sdk_insert'
    ) THEN
        CREATE POLICY log_batch_sdk_insert ON apploggers.log_batches
            FOR INSERT TO anon
            WITH CHECK (
                batch_id IS NOT NULL
                AND event_count > 0
                AND BTRIM(COALESCE(batch_hash, '')) <> ''
                AND CHAR_LENGTH(batch_hash) <= 256
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'apploggers' AND tablename = 'log_batches' AND policyname = 'log_batch_service_all'
    ) THEN
        CREATE POLICY log_batch_service_all ON apploggers.log_batches
            FOR ALL TO service_role
            USING (true)
            WITH CHECK (true);
    END IF;
END $$;

COMMENT ON TABLE apploggers.log_batches IS
    'Centralized batch tracking for log ingestion with integrity verification keys';

COMMENT ON COLUMN apploggers.log_batches.key_id IS
    'Logical identifier of the integrity secret used to sign this log batch.';

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 2: Create metric_batches table IN apploggers schema  
-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS apploggers.metric_batches (
    batch_id UUID PRIMARY KEY,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_count INT NOT NULL CHECK (event_count > 0),
    batch_hash TEXT NOT NULL DEFAULT '',
    environment VARCHAR(50) NULL
        CHECK (environment IS NULL OR environment IN ('development', 'staging', 'production', 'test')),
    sdk_version VARCHAR(20) NULL,
    key_id VARCHAR(64) NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT metric_batches_batch_hash_min_length CHECK (CHAR_LENGTH(batch_hash) > 0)
);

CREATE INDEX IF NOT EXISTS idx_apploggers_metric_batches_sent_at
    ON apploggers.metric_batches (sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_metric_batches_key_id
    ON apploggers.metric_batches (key_id);

ALTER TABLE apploggers.metric_batches ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'apploggers' AND tablename = 'metric_batches' AND policyname = 'metric_batch_sdk_insert'
    ) THEN
        CREATE POLICY metric_batch_sdk_insert ON apploggers.metric_batches
            FOR INSERT TO anon
            WITH CHECK (
                batch_id IS NOT NULL
                AND event_count > 0
                AND BTRIM(COALESCE(batch_hash, '')) <> ''
                AND CHAR_LENGTH(batch_hash) <= 256
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'apploggers' AND tablename = 'metric_batches' AND policyname = 'metric_batch_service_all'
    ) THEN
        CREATE POLICY metric_batch_service_all ON apploggers.metric_batches
            FOR ALL TO service_role
            USING (true)
            WITH CHECK (true);
    END IF;
END $$;

COMMENT ON TABLE apploggers.metric_batches IS
    'Centralized batch tracking for metric ingestion with integrity verification keys';

COMMENT ON COLUMN apploggers.metric_batches.key_id IS
    'Logical identifier of the integrity secret used to sign this metric batch.';

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 3: Add batch tracking columns to apploggers.app_logs
-- ──────────────────────────────────────────────────────────────────────────────

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'apploggers' AND table_name = 'app_logs' AND table_type = 'BASE TABLE'
    ) THEN
        ALTER TABLE apploggers.app_logs
            ADD COLUMN IF NOT EXISTS batch_id UUID NULL REFERENCES apploggers.log_batches(batch_id)
                DEFERRABLE INITIALLY DEFERRED;

        CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_batch_id
            ON apploggers.app_logs (batch_id)
            WHERE batch_id IS NOT NULL;
    END IF;
END $$;

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 4: Add batch tracking columns to apploggers.app_metrics
-- ──────────────────────────────────────────────────────────────────────────────

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'apploggers' AND table_name = 'app_metrics' AND table_type = 'BASE TABLE'
    ) THEN
        ALTER TABLE apploggers.app_metrics
            ADD COLUMN IF NOT EXISTS timestamp BIGINT NULL,
            ADD COLUMN IF NOT EXISTS batch_id UUID NULL REFERENCES apploggers.metric_batches(batch_id)
                DEFERRABLE INITIALLY DEFERRED;

        CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_batch_id
            ON apploggers.app_metrics (batch_id)
            WHERE batch_id IS NOT NULL;
    END IF;
END $$;

COMMENT ON COLUMN apploggers.app_metrics.timestamp IS
    'Client-side epoch millis for batch integrity verification and chronological ordering';

COMMENT ON COLUMN apploggers.app_metrics.batch_id IS
    'Foreign key to metric batch for integrity verification';

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 5: Atomic log batch ingestion function IN apploggers
-- ──────────────────────────────────────────────────────────────────────────────

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
    v_environment VARCHAR(50);
    v_sdk_version VARCHAR(20);
BEGIN
    -- Validate input
    IF log_entries IS NULL OR jsonb_typeof(log_entries) <> 'array' OR jsonb_array_length(log_entries) = 0 THEN
        RAISE EXCEPTION 'log_entries must be a non-empty JSON array';
    END IF;

    IF manifest IS NOT NULL THEN
        manifest_batch_id := NULLIF(TRIM(manifest->>'batch_id'), '')::UUID;
        IF manifest_batch_id IS NULL THEN
            RAISE EXCEPTION 'manifest.batch_id is required AND must be valid UUID when manifest is provided';
        END IF;
    END IF;

    -- Ingest log entries
    INSERT INTO apploggers.app_logs (
        id, level, tag, message, environment, throwable_type, throwable_msg,
        stack_trace, device_info, api_level, sdk_version, session_id, device_id,
        user_id, extra, anomaly_type, trace_id, variant, batch_id, timestamp
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

    -- Track batch metadata
    IF manifest IS NOT NULL THEN
        v_environment := NULLIF(TRIM(manifest->>'environment'), '');
        v_sdk_version := NULLIF(TRIM(manifest->>'sdk_version'), '');

        INSERT INTO apploggers.log_batches (
            batch_id, event_count, batch_hash, environment, sdk_version, key_id
        ) VALUES (
            manifest_batch_id,
            COALESCE(NULLIF(TRIM(manifest->>'event_count'), '')::INT, inserted_count),
            COALESCE(NULLIF(TRIM(manifest->>'batch_hash'), ''), ''),
            v_environment,
            v_sdk_version,
            NULLIF(TRIM(manifest->>'key_id'), '')
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

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 6: Atomic metric batch ingestion function IN apploggers
-- ──────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION apploggers.ingest_metric_batch(
    metric_entries JSONB,
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
    v_environment VARCHAR(50);
    v_sdk_version VARCHAR(20);
BEGIN
    -- Validate input
    IF metric_entries IS NULL OR jsonb_typeof(metric_entries) <> 'array' OR jsonb_array_length(metric_entries) = 0 THEN
        RAISE EXCEPTION 'metric_entries must be a non-empty JSON array';
    END IF;

    IF manifest IS NOT NULL THEN
        manifest_batch_id := NULLIF(TRIM(manifest->>'batch_id'), '')::UUID;
        IF manifest_batch_id IS NULL THEN
            RAISE EXCEPTION 'manifest.batch_id is required AND must be valid UUID when manifest is provided';
        END IF;
    END IF;

    -- Ingest metric entries
    INSERT INTO apploggers.app_metrics (
        id, name, value, unit, tags, environment, device_id, session_id,
        sdk_version, user_id, timestamp, batch_id
    )
    SELECT
        COALESCE(NULLIF(entry->>'id', '')::UUID, gen_random_uuid()),
        COALESCE(entry->>'name', ''),
        COALESCE(NULLIF(entry->>'value', '')::DOUBLE PRECISION, 0.0),
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
        END
    FROM jsonb_array_elements(metric_entries) AS entry;

    GET DIAGNOSTICS inserted_count = ROW_COUNT;

    -- Track batch metadata
    IF manifest IS NOT NULL THEN
        v_environment := NULLIF(TRIM(manifest->>'environment'), '');
        v_sdk_version := NULLIF(TRIM(manifest->>'sdk_version'), '');

        INSERT INTO apploggers.metric_batches (
            batch_id, event_count, batch_hash, environment, sdk_version, key_id
        ) VALUES (
            manifest_batch_id,
            COALESCE(NULLIF(TRIM(manifest->>'event_count'), '')::INT, inserted_count),
            COALESCE(NULLIF(TRIM(manifest->>'batch_hash'), ''), ''),
            v_environment,
            v_sdk_version,
            NULLIF(TRIM(manifest->>'key_id'), '')
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

-- ──────────────────────────────────────────────────────────────────────────────
-- Step 7: Grant execution privileges
-- ──────────────────────────────────────────────────────────────────────────────

GRANT EXECUTE ON FUNCTION apploggers.ingest_log_batch(JSONB, JSONB) TO anon, service_role;
GRANT EXECUTE ON FUNCTION apploggers.ingest_metric_batch(JSONB, JSONB) TO anon, service_role;

GRANT INSERT ON apploggers.log_batches TO anon;
GRANT INSERT ON apploggers.metric_batches TO anon;
