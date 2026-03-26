-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration 016: Add user_id to app_metrics for GDPR erasure
-- ═══════════════════════════════════════════════════════════════════════════════
-- The GDPR Art. 17 erase command cascades to app_metrics, but the table
-- only has device_id (migration 002). When a user requests erasure by
-- user_id, we cannot match metrics without this column.
--
-- The SDK already sends user_id in its metric payload when available.
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE app_metrics
    ADD COLUMN IF NOT EXISTS user_id TEXT NULL;

COMMENT ON COLUMN app_metrics.user_id IS 'Optional user identifier set via AppLoggerSDK.setUserProperties(). Used for GDPR Art. 17 right-to-erasure. NULL for anonymous metrics.';

-- Index for GDPR erasure queries
CREATE INDEX IF NOT EXISTS idx_app_metrics_user_id
    ON app_metrics (user_id)
    WHERE user_id IS NOT NULL;
