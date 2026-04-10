-- Migration 019: versioned integrity keys + separated metric batch verification

ALTER TABLE public.log_batches
    ADD COLUMN IF NOT EXISTS key_id VARCHAR(64) NULL;

COMMENT ON COLUMN public.log_batches.key_id IS
    'Logical identifier of the integrity secret used to sign this log batch.';

CREATE OR REPLACE FUNCTION public.ingest_log_batch(
    log_entries JSONB,
    manifest JSONB DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
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

    INSERT INTO public.app_logs (
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
        INSERT INTO public.log_batches (
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

CREATE OR REPLACE FUNCTION apploggers.ingest_log_batch(
    log_entries JSONB,
    manifest JSONB DEFAULT NULL
)
RETURNS JSONB
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.ingest_log_batch(log_entries, manifest);
$$;

GRANT EXECUTE ON FUNCTION public.ingest_log_batch(JSONB, JSONB) TO anon, service_role;
GRANT EXECUTE ON FUNCTION apploggers.ingest_log_batch(JSONB, JSONB) TO anon, service_role;

CREATE TABLE IF NOT EXISTS public.metric_batches (
    batch_id UUID PRIMARY KEY,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_count INT NOT NULL,
    batch_hash TEXT NOT NULL DEFAULT '',
    environment VARCHAR(50) NULL,
    sdk_version VARCHAR(20) NULL,
    key_id VARCHAR(64) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_metric_batches_sent_at ON public.metric_batches (sent_at DESC);

ALTER TABLE public.metric_batches ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'metric_batches' AND policyname = 'metric_batch_sdk_insert'
    ) THEN
        CREATE POLICY metric_batch_sdk_insert ON public.metric_batches
            FOR INSERT TO anon
            WITH CHECK (true);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public' AND tablename = 'metric_batches' AND policyname = 'metric_batch_service_all'
    ) THEN
        CREATE POLICY metric_batch_service_all ON public.metric_batches
            FOR ALL TO service_role
            USING (true)
            WITH CHECK (true);
    END IF;
END $$;

ALTER TABLE public.app_metrics
    ADD COLUMN IF NOT EXISTS timestamp BIGINT NULL,
    ADD COLUMN IF NOT EXISTS batch_id UUID NULL;

COMMENT ON COLUMN public.app_metrics.timestamp IS
    'Client-side epoch millis used for metric batch integrity verification.';

CREATE INDEX IF NOT EXISTS idx_app_metrics_batch_id
    ON public.app_metrics (batch_id)
    WHERE batch_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'app_metrics_batch_id_fkey'
    ) THEN
        ALTER TABLE public.app_metrics
            ADD CONSTRAINT app_metrics_batch_id_fkey
            FOREIGN KEY (batch_id)
            REFERENCES public.metric_batches(batch_id)
            DEFERRABLE INITIALLY DEFERRED;
    END IF;
END $$;

CREATE OR REPLACE FUNCTION public.ingest_metric_batch(
    metric_entries JSONB,
    manifest JSONB DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
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

    INSERT INTO public.app_metrics (
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
        batch_id
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
        END
    FROM jsonb_array_elements(metric_entries) AS entry;

    GET DIAGNOSTICS inserted_count = ROW_COUNT;

    IF manifest IS NOT NULL THEN
        INSERT INTO public.metric_batches (
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
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.ingest_metric_batch(metric_entries, manifest);
$$;

GRANT EXECUTE ON FUNCTION public.ingest_metric_batch(JSONB, JSONB) TO anon, service_role;
GRANT EXECUTE ON FUNCTION apploggers.ingest_metric_batch(JSONB, JSONB) TO anon, service_role;

CREATE OR REPLACE VIEW apploggers.metric_batches AS
SELECT * FROM public.metric_batches;

CREATE OR REPLACE VIEW apploggers.log_batches AS
SELECT * FROM public.log_batches;

CREATE OR REPLACE VIEW apploggers.app_metrics AS
SELECT * FROM public.app_metrics;