-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration 012: Enterprise indexes + analytics views
-- ═══════════════════════════════════════════════════════════════════════════════
-- Addresses:
--   P0: Missing trace_id index (critical for distributed tracing)
--   P0: Missing GIN index on extra JSONB (CLI --package / --error-code performance)
--   P1: Composite indexes for 3-column filter patterns (CLI query acceleration)
--   P1: Analytics views for telemetry stats / aggregation commands
-- ═══════════════════════════════════════════════════════════════════════════════

-- ── P0: trace_id column ─────────────────────────────────────────────────────
-- Required for distributed tracing: SDK sends setTraceId() values here.
-- Must exist BEFORE index creation below.
ALTER TABLE app_logs ADD COLUMN IF NOT EXISTS trace_id TEXT NULL;

-- ── P0: trace_id index ──────────────────────────────────────────────────────
-- Enables: apploggers telemetry query --trace-id <uuid>
-- Without this index, trace correlation across devices is a full table scan.
CREATE INDEX IF NOT EXISTS idx_app_logs_trace_id
    ON app_logs (trace_id)
    WHERE trace_id IS NOT NULL;

-- ── P0: GIN index on extra JSONB ────────────────────────────────────────────
-- Enables: apploggers telemetry query --extra-key foo --extra-value bar
-- Enables: apploggers telemetry query --package com.company.billing
-- Enables: apploggers telemetry query --error-code E-42
-- GIN supports containment (@>) and key-exists (?) operators for any JSONB field.
CREATE INDEX IF NOT EXISTS idx_app_logs_extra_gin
    ON app_logs USING GIN (extra)
    WHERE extra IS NOT NULL;

-- ── P0: GIN index on metrics tags JSONB ─────────────────────────────────────
-- Enables efficient filtering on metric tags (platform, app_version, device_model).
CREATE INDEX IF NOT EXISTS idx_app_metrics_tags_gin
    ON app_metrics USING GIN (tags)
    WHERE tags IS NOT NULL;

-- ── P1: Composite indexes for common CLI multi-filter patterns ──────────────

-- Pattern: apploggers telemetry query --device-id X --severity ERROR --from T
CREATE INDEX IF NOT EXISTS idx_app_logs_device_level_created
    ON app_logs (device_id, level, created_at DESC);

-- Pattern: apploggers telemetry query --tag AUTH --user-id Y (GDPR user trace)
CREATE INDEX IF NOT EXISTS idx_app_logs_tag_user_created
    ON app_logs (tag, user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

-- Pattern: apploggers telemetry query --trace-id X (sorted timeline)
CREATE INDEX IF NOT EXISTS idx_app_logs_trace_created
    ON app_logs (trace_id, created_at ASC)
    WHERE trace_id IS NOT NULL;

-- ── P1: Analytics views ─────────────────────────────────────────────────────

-- View: Session-level summary (used by `telemetry stats --aggregate session`)
CREATE OR REPLACE VIEW session_summary AS
SELECT
    session_id,
    device_id,
    user_id,
    environment,
    COUNT(*)                                              AS event_count,
    COUNT(*) FILTER (WHERE level = 'ERROR')               AS error_count,
    COUNT(*) FILTER (WHERE level = 'CRITICAL')            AS critical_count,
    MIN(created_at)                                       AS first_event,
    MAX(created_at)                                       AS last_event,
    MAX(created_at) - MIN(created_at)                     AS session_duration
FROM app_logs
GROUP BY session_id, device_id, user_id, environment;

-- View: Hourly error rate (used by `telemetry stats --aggregate hour`)
CREATE OR REPLACE VIEW hourly_error_rate AS
SELECT
    DATE_TRUNC('hour', created_at)                        AS hour,
    environment,
    COUNT(*)                                              AS total_events,
    COUNT(*) FILTER (WHERE level IN ('ERROR', 'CRITICAL'))AS error_events,
    ROUND(
        COUNT(*) FILTER (WHERE level IN ('ERROR', 'CRITICAL'))::numeric
        / NULLIF(COUNT(*), 0) * 100, 2
    )                                                     AS error_rate_pct
FROM app_logs
GROUP BY DATE_TRUNC('hour', created_at), environment;

-- View: Device health breakdown (used by `explain` correlations)
CREATE OR REPLACE VIEW device_health AS
SELECT
    device_info->>'model'      AS device_model,
    device_info->>'os_version' AS os_version,
    environment,
    COUNT(*)                                              AS total_events,
    COUNT(*) FILTER (WHERE level IN ('ERROR', 'CRITICAL'))AS error_events,
    COUNT(DISTINCT session_id)                            AS session_count
FROM app_logs
GROUP BY device_info->>'model', device_info->>'os_version', environment;

-- View: Hourly metric aggregation (used by `telemetry stats --source metrics`)
CREATE OR REPLACE VIEW hourly_metrics_summary AS
SELECT
    DATE_TRUNC('hour', created_at)                        AS hour,
    name,
    environment,
    COUNT(*)                                              AS sample_count,
    ROUND(AVG(value)::numeric, 4)                         AS avg_value,
    MIN(value)                                            AS min_value,
    MAX(value)                                            AS max_value,
    ROUND(STDDEV(value)::numeric, 4)                      AS stddev_value,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY value)   AS p95_value,
    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY value)   AS p99_value
FROM app_metrics
GROUP BY DATE_TRUNC('hour', created_at), name, environment;

-- ── RLS for views ───────────────────────────────────────────────────────────
-- Views inherit the base table's RLS. Service-role (CLI) has full access.
-- No additional policies needed for read-only views.
