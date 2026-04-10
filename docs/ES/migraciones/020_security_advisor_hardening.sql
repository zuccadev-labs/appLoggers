-- Migration 020: security advisor hardening for analytics views, helper functions,
-- and anon insert policies.

-- ── Analytics views: execute with caller permissions, not definer permissions ──
ALTER VIEW IF EXISTS public.session_summary
    SET (security_invoker = true);

ALTER VIEW IF EXISTS public.hourly_error_rate
    SET (security_invoker = true);

ALTER VIEW IF EXISTS public.device_health
    SET (security_invoker = true);

ALTER VIEW IF EXISTS public.hourly_metrics_summary
    SET (security_invoker = true);

-- ── Helper functions: pin search_path to avoid role-mutable lookup ──────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public' AND p.proname = 'update_device_config_timestamp'
    ) THEN
        ALTER FUNCTION public.update_device_config_timestamp() SET search_path = public;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public' AND p.proname = 'correlate_beta_tester_email'
    ) THEN
        ALTER FUNCTION public.correlate_beta_tester_email() SET search_path = public;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public' AND p.proname = 'expire_beta_tester_mappings'
    ) THEN
        ALTER FUNCTION public.expire_beta_tester_mappings() SET search_path = public;
    END IF;
END $$;

-- ── RLS: replace unconditional anon insert/upsert policies with shape checks ─
DROP POLICY IF EXISTS sdk_insert_logs ON public.app_logs;
CREATE POLICY sdk_insert_logs ON public.app_logs
    FOR INSERT
    TO anon
    WITH CHECK (
        level IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL')
        AND NULLIF(BTRIM(tag), '') IS NOT NULL
        AND NULLIF(BTRIM(message), '') IS NOT NULL
        AND NULLIF(BTRIM(session_id), '') IS NOT NULL
    );

DROP POLICY IF EXISTS sdk_insert_metrics ON public.app_metrics;
CREATE POLICY sdk_insert_metrics ON public.app_metrics
    FOR INSERT
    TO anon
    WITH CHECK (
        NULLIF(BTRIM(name), '') IS NOT NULL
        AND NULLIF(BTRIM(session_id), '') IS NOT NULL
        AND NULLIF(BTRIM(unit), '') IS NOT NULL
        AND value = value
        AND value <> 'Infinity'::DOUBLE PRECISION
        AND value <> '-Infinity'::DOUBLE PRECISION
    );

DROP POLICY IF EXISTS batch_sdk_insert ON public.log_batches;
CREATE POLICY batch_sdk_insert ON public.log_batches
    FOR INSERT
    TO anon
    WITH CHECK (
        batch_id IS NOT NULL
        AND event_count > 0
        AND BTRIM(COALESCE(batch_hash, '')) <> ''
        AND CHAR_LENGTH(batch_hash) <= 128
    );

DROP POLICY IF EXISTS metric_batch_sdk_insert ON public.metric_batches;
CREATE POLICY metric_batch_sdk_insert ON public.metric_batches
    FOR INSERT
    TO anon
    WITH CHECK (
        batch_id IS NOT NULL
        AND event_count > 0
        AND BTRIM(COALESCE(batch_hash, '')) <> ''
        AND CHAR_LENGTH(batch_hash) <= 128
    );

DROP POLICY IF EXISTS beta_tester_sdk_upsert ON public.beta_tester_devices;

CREATE POLICY beta_tester_sdk_select ON public.beta_tester_devices
    FOR SELECT
    TO anon
    USING (NULLIF(BTRIM(device_id), '') IS NOT NULL);

CREATE POLICY beta_tester_sdk_insert ON public.beta_tester_devices
    FOR INSERT
    TO anon
    WITH CHECK (
        NULLIF(BTRIM(device_id), '') IS NOT NULL
        AND POSITION('@' IN COALESCE(email, '')) > 1
        AND updated_at IS NOT NULL
    );

CREATE POLICY beta_tester_sdk_update ON public.beta_tester_devices
    FOR UPDATE
    TO anon
    USING (NULLIF(BTRIM(device_id), '') IS NOT NULL)
    WITH CHECK (
        NULLIF(BTRIM(device_id), '') IS NOT NULL
        AND POSITION('@' IN COALESCE(email, '')) > 1
        AND updated_at IS NOT NULL
    );