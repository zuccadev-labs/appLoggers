-- Migration 002: AppLoggers indexes and analytics views
-- Scope: performance indexes and read models for operational analysis.

SET search_path = apploggers, pg_catalog;

CREATE INDEX IF NOT EXISTS idx_apploggers_log_batches_sent_at ON apploggers.log_batches (sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_log_batches_key_id ON apploggers.log_batches (key_id);
CREATE INDEX IF NOT EXISTS idx_apploggers_metric_batches_sent_at ON apploggers.metric_batches (sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_metric_batches_key_id ON apploggers.metric_batches (key_id);

CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_created_at ON apploggers.app_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_level ON apploggers.app_logs (level);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_session_id ON apploggers.app_logs (session_id);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_device_id ON apploggers.app_logs (device_id);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_tag ON apploggers.app_logs (tag);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_app_package ON apploggers.app_logs (app_package);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_source_scope ON apploggers.app_logs (source_scope);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_trace_id ON apploggers.app_logs (trace_id) WHERE trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_variant ON apploggers.app_logs (variant) WHERE variant IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_environment ON apploggers.app_logs (environment) WHERE environment IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_anomaly_type ON apploggers.app_logs (anomaly_type) WHERE anomaly_type IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_batch_id ON apploggers.app_logs (batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_sdk_version ON apploggers.app_logs (sdk_version) WHERE sdk_version IS NOT NULL AND sdk_version <> '0.0.0';
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_user_id ON apploggers.app_logs (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_level_created ON apploggers.app_logs (level, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_session_created ON apploggers.app_logs (session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_env_level ON apploggers.app_logs (environment, level, created_at DESC) WHERE environment IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_device_level_created ON apploggers.app_logs (device_id, level, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_tag_user_created ON apploggers.app_logs (tag, user_id, created_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_trace_created ON apploggers.app_logs (trace_id, created_at ASC) WHERE trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_extra_gin ON apploggers.app_logs USING GIN (extra) WHERE extra IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_logs_device_platform ON apploggers.app_logs ((device_info->>'platform'));

CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_created_at ON apploggers.app_metrics (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_name ON apploggers.app_metrics (name);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_session_id ON apploggers.app_metrics (session_id);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_device_id ON apploggers.app_metrics (device_id);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_app_package ON apploggers.app_metrics (app_package);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_source_scope ON apploggers.app_metrics (source_scope);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_environment ON apploggers.app_metrics (environment) WHERE environment IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_env_name ON apploggers.app_metrics (environment, name, created_at DESC) WHERE environment IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_sdk_version ON apploggers.app_metrics (sdk_version) WHERE sdk_version IS NOT NULL AND sdk_version <> '0.0.0';
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_user_id ON apploggers.app_metrics (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_batch_id ON apploggers.app_metrics (batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_name_created ON apploggers.app_metrics (name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_session_created ON apploggers.app_metrics (session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_apploggers_app_metrics_tags_gin ON apploggers.app_metrics USING GIN (tags) WHERE tags IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_apploggers_device_remote_config_fingerprint ON apploggers.device_remote_config (device_fingerprint) WHERE enabled = true;
CREATE INDEX IF NOT EXISTS idx_apploggers_device_remote_config_environment ON apploggers.device_remote_config (environment) WHERE enabled = true;

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
