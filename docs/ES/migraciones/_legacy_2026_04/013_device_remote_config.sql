-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration 013: Remote device configuration
-- ═══════════════════════════════════════════════════════════════════════════════
-- Enables remote debug control per-device or globally.
--
-- Use cases:
--   1. Global: set min_level=ERROR for all devices (production default)
--   2. Per-device: set min_level=DEBUG for a specific device to debug a user issue
--   3. Tag filtering: only capture Auth/Network tags for a device
--   4. Sampling: capture 10% of events globally to reduce volume
--
-- The SDK polls this table on init + every N seconds (default: 300s).
-- Device-specific rules override global rules (device_fingerprint NOT NULL wins).
--
-- Device fingerprint:
--   Android: Settings.Secure.ANDROID_ID (persists across reinstalls)
--   iOS: identifierForVendor (persists across reinstalls from same vendor)
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS device_remote_config (
    id              UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_fingerprint TEXT,                     -- NULL = global rule (applies to ALL devices)
    environment     VARCHAR(50),                 -- NULL = all environments
    min_level       VARCHAR(20) DEFAULT 'ERROR'
                    CHECK (min_level IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL')),
    debug_enabled   BOOLEAN DEFAULT false,       -- Enable debug/console output remotely
    tags_allow      TEXT[],                      -- NULL = all tags allowed; non-NULL = only these tags pass
    tags_block      TEXT[],                      -- NULL = no tags blocked; non-NULL = these tags are dropped
    sampling_rate   DOUBLE PRECISION DEFAULT 1.0
                    CHECK (sampling_rate >= 0.0 AND sampling_rate <= 1.0),
    enabled         BOOLEAN DEFAULT true,        -- Soft-disable without deleting
    notes           TEXT,                        -- Admin notes (e.g. "Debug user issue #123")
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Index for SDK polling: fetch by fingerprint (device-specific + global fallback)
CREATE INDEX IF NOT EXISTS idx_device_remote_config_fingerprint
    ON device_remote_config (device_fingerprint)
    WHERE enabled = true;

-- Index for CLI listing by environment
CREATE INDEX IF NOT EXISTS idx_device_remote_config_environment
    ON device_remote_config (environment)
    WHERE enabled = true;

-- ── RLS policies ────────────────────────────────────────────────────────────
-- SDK (anon key) needs SELECT to fetch its own config.
-- CLI (service role) needs full CRUD for management.
ALTER TABLE device_remote_config ENABLE ROW LEVEL SECURITY;

-- SDK: read-only access (anon key can only SELECT enabled configs)
CREATE POLICY device_config_sdk_read ON device_remote_config
    FOR SELECT TO anon
    USING (enabled = true);

-- CLI / admin: full access via service role
CREATE POLICY device_config_service_all ON device_remote_config
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

-- ── Helper: update timestamp trigger ────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_device_config_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_device_remote_config_updated
    BEFORE UPDATE ON device_remote_config
    FOR EACH ROW
    EXECUTE FUNCTION update_device_config_timestamp();
