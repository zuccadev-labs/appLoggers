-- Migration 026: custom RLS hardening for operational telemetry tables in apploggers
-- Objective:
--   1) Force RLS on app_logs and app_metrics.
--   2) Keep SDK writes constrained to anon INSERT with payload-shape checks.
--   3) Keep operational reads under service_role only.

ALTER TABLE apploggers.app_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE apploggers.app_metrics ENABLE ROW LEVEL SECURITY;

ALTER TABLE apploggers.app_logs FORCE ROW LEVEL SECURITY;
ALTER TABLE apploggers.app_metrics FORCE ROW LEVEL SECURITY;

-- Replace previous policies with explicit custom policies for operational model.
DROP POLICY IF EXISTS sdk_insert_logs ON apploggers.app_logs;
DROP POLICY IF EXISTS monitor_read_logs ON apploggers.app_logs;

CREATE POLICY sdk_insert_logs ON apploggers.app_logs
    FOR INSERT
    TO anon
    WITH CHECK (
        level IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL')
        AND NULLIF(BTRIM(tag), '') IS NOT NULL
        AND NULLIF(BTRIM(message), '') IS NOT NULL
        AND NULLIF(BTRIM(session_id), '') IS NOT NULL
        AND (
            environment IS NULL
            OR environment IN ('development', 'staging', 'production', 'test')
        )
    );

CREATE POLICY monitor_read_logs ON apploggers.app_logs
    FOR SELECT
    TO service_role
    USING (true);

DROP POLICY IF EXISTS sdk_insert_metrics ON apploggers.app_metrics;
DROP POLICY IF EXISTS monitor_read_metrics ON apploggers.app_metrics;

CREATE POLICY sdk_insert_metrics ON apploggers.app_metrics
    FOR INSERT
    TO anon
    WITH CHECK (
        NULLIF(BTRIM(name), '') IS NOT NULL
        AND NULLIF(BTRIM(session_id), '') IS NOT NULL
        AND NULLIF(BTRIM(unit), '') IS NOT NULL
        AND isfinite(value)
        AND (
            environment IS NULL
            OR environment IN ('development', 'staging', 'production', 'test')
        )
    );

CREATE POLICY monitor_read_metrics ON apploggers.app_metrics
    FOR SELECT
    TO service_role
    USING (true);
