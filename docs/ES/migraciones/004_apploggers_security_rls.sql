-- Migration 004: AppLoggers security and row-level security model
-- Scope: enforce RLS and create role-specific write/read policies.

SET search_path = apploggers, pg_catalog;

ALTER TABLE apploggers.app_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.app_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.log_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.metric_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.device_remote_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.beta_tester_devices ENABLE ROW LEVEL SECURITY;

ALTER TABLE apploggers.app_logs FORCE ROW LEVEL SECURITY;
ALTER TABLE apploggers.app_metrics FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS sdk_insert_logs ON apploggers.app_logs;
DROP POLICY IF EXISTS monitor_read_logs ON apploggers.app_logs;
CREATE POLICY sdk_insert_logs ON apploggers.app_logs
    FOR INSERT TO anon
    WITH CHECK (
        level IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL')
        AND NULLIF(BTRIM(tag), '') IS NOT NULL
        AND NULLIF(BTRIM(message), '') IS NOT NULL
        AND NULLIF(BTRIM(session_id), '') IS NOT NULL
        AND (environment IS NULL OR environment IN ('development', 'staging', 'production', 'test'))
    );
CREATE POLICY monitor_read_logs ON apploggers.app_logs
    FOR SELECT TO service_role
    USING (true);

DROP POLICY IF EXISTS sdk_insert_metrics ON apploggers.app_metrics;
DROP POLICY IF EXISTS monitor_read_metrics ON apploggers.app_metrics;
CREATE POLICY sdk_insert_metrics ON apploggers.app_metrics
    FOR INSERT TO anon
    WITH CHECK (
        NULLIF(BTRIM(name), '') IS NOT NULL
        AND NULLIF(BTRIM(session_id), '') IS NOT NULL
        AND NULLIF(BTRIM(unit), '') IS NOT NULL
        AND value = value
        AND value > '-Infinity'::float8
        AND value < 'Infinity'::float8
        AND (environment IS NULL OR environment IN ('development', 'staging', 'production', 'test'))
    );
CREATE POLICY monitor_read_metrics ON apploggers.app_metrics
    FOR SELECT TO service_role
    USING (true);

DROP POLICY IF EXISTS batch_sdk_insert ON apploggers.log_batches;
DROP POLICY IF EXISTS batch_service_all ON apploggers.log_batches;
CREATE POLICY batch_sdk_insert ON apploggers.log_batches
    FOR INSERT TO anon
    WITH CHECK (
        batch_id IS NOT NULL
        AND event_count > 0
        AND BTRIM(COALESCE(batch_hash, '')) <> ''
        AND CHAR_LENGTH(batch_hash) <= 256
    );
CREATE POLICY batch_service_all ON apploggers.log_batches
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS metric_batch_sdk_insert ON apploggers.metric_batches;
DROP POLICY IF EXISTS metric_batch_service_all ON apploggers.metric_batches;
CREATE POLICY metric_batch_sdk_insert ON apploggers.metric_batches
    FOR INSERT TO anon
    WITH CHECK (
        batch_id IS NOT NULL
        AND event_count > 0
        AND BTRIM(COALESCE(batch_hash, '')) <> ''
        AND CHAR_LENGTH(batch_hash) <= 256
    );
CREATE POLICY metric_batch_service_all ON apploggers.metric_batches
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS device_config_sdk_read ON apploggers.device_remote_config;
DROP POLICY IF EXISTS device_config_service_all ON apploggers.device_remote_config;
CREATE POLICY device_config_sdk_read ON apploggers.device_remote_config
    FOR SELECT TO anon
    USING (enabled = true);
CREATE POLICY device_config_service_all ON apploggers.device_remote_config
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS beta_tester_sdk_select ON apploggers.beta_tester_devices;
DROP POLICY IF EXISTS beta_tester_sdk_insert ON apploggers.beta_tester_devices;
DROP POLICY IF EXISTS beta_tester_sdk_update ON apploggers.beta_tester_devices;
DROP POLICY IF EXISTS beta_tester_service_all ON apploggers.beta_tester_devices;

CREATE POLICY beta_tester_sdk_select ON apploggers.beta_tester_devices
    FOR SELECT TO anon
    USING (NULLIF(BTRIM(device_id), '') IS NOT NULL);

CREATE POLICY beta_tester_sdk_insert ON apploggers.beta_tester_devices
    FOR INSERT TO anon
    WITH CHECK (
        NULLIF(BTRIM(device_id), '') IS NOT NULL
        AND POSITION('@' IN COALESCE(email, '')) > 1
        AND updated_at IS NOT NULL
    );

CREATE POLICY beta_tester_sdk_update ON apploggers.beta_tester_devices
    FOR UPDATE TO anon
    USING (NULLIF(BTRIM(device_id), '') IS NOT NULL)
    WITH CHECK (
        NULLIF(BTRIM(device_id), '') IS NOT NULL
        AND POSITION('@' IN COALESCE(email, '')) > 1
        AND updated_at IS NOT NULL
    );

CREATE POLICY beta_tester_service_all ON apploggers.beta_tester_devices
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

ALTER VIEW apploggers.session_summary SET (security_invoker = true);
ALTER VIEW apploggers.hourly_error_rate SET (security_invoker = true);
ALTER VIEW apploggers.device_health SET (security_invoker = true);
ALTER VIEW apploggers.hourly_metrics_summary SET (security_invoker = true);
