-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration 014: Beta tester email auto-correlation
-- ═══════════════════════════════════════════════════════════════════════════════
-- Solves: Frontend app knows the beta tester's email, backend app on the same
-- device does NOT. Both apps share the same device_id. This trigger
-- auto-fills beta_tester_email on backend events by looking up the
-- device_id → email mapping stored by the frontend app.
--
-- Flow:
--   1. Frontend inserts event with: is_beta_tester=true, beta_tester_email=X, device_id=Y
--   2. Trigger stores mapping: device_id Y → email X in beta_tester_devices
--   3. Backend inserts event with: is_beta_tester=true, beta_tester_email=NULL, device_id=Y
--   4. Trigger looks up device_id Y → finds email X → backfills it
--
-- The lookup table is lightweight (one row per device) and auto-expires
-- entries older than 90 days to comply with data minimization (GDPR Art. 5.1.e).
-- ═══════════════════════════════════════════════════════════════════════════════

-- ── Lookup table: device_id → beta tester email ──────────────────────────────
CREATE TABLE IF NOT EXISTS beta_tester_devices (
    device_id   TEXT PRIMARY KEY,
    email       TEXT NOT NULL,
    app_package TEXT,
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- RLS: SDK (anon) can read/write; CLI (service_role) has full access
ALTER TABLE beta_tester_devices ENABLE ROW LEVEL SECURITY;

CREATE POLICY beta_tester_sdk_upsert ON beta_tester_devices
    FOR ALL TO anon
    USING (true)
    WITH CHECK (true);

CREATE POLICY beta_tester_service_all ON beta_tester_devices
    FOR ALL TO service_role
    USING (true)
    WITH CHECK (true);

-- ── Trigger function: auto-correlate beta tester email ───────────────────────
CREATE OR REPLACE FUNCTION correlate_beta_tester_email()
RETURNS TRIGGER AS $$
DECLARE
    is_beta    BOOLEAN;
    has_email  BOOLEAN;
    tester_email TEXT;
    lookup_email TEXT;
BEGIN
    -- Only process beta tester events
    is_beta := (NEW.extra->>'is_beta_tester') = 'true';
    IF NOT is_beta THEN
        RETURN NEW;
    END IF;

    tester_email := NEW.extra->>'beta_tester_email';
    has_email := tester_email IS NOT NULL AND tester_email != '';

    IF has_email THEN
        -- Case 1: Frontend app — HAS email → store the mapping for backend apps
        INSERT INTO beta_tester_devices (device_id, email, app_package, updated_at)
        VALUES (
            NEW.device_id,
            tester_email,
            NEW.extra->>'app_package',
            NOW()
        )
        ON CONFLICT (device_id) DO UPDATE
        SET email = EXCLUDED.email,
            app_package = EXCLUDED.app_package,
            updated_at = NOW();
    ELSE
        -- Case 2: Backend app — NO email → look up from mapping table
        SELECT email INTO lookup_email
        FROM beta_tester_devices
        WHERE device_id = NEW.device_id;

        IF lookup_email IS NOT NULL THEN
            -- Backfill the email into the event's extra JSONB
            NEW.extra := jsonb_set(
                COALESCE(NEW.extra, '{}'::jsonb),
                '{beta_tester_email}',
                to_jsonb(lookup_email)
            );
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── Attach trigger to app_logs ───────────────────────────────────────────────
CREATE TRIGGER trg_correlate_beta_tester
    BEFORE INSERT ON app_logs
    FOR EACH ROW
    EXECUTE FUNCTION correlate_beta_tester_email();

-- ── Cleanup: auto-expire stale mappings (GDPR Art. 5.1.e — data minimization)
-- Run periodically via pg_cron or Supabase scheduled function:
--   SELECT expire_beta_tester_mappings();
CREATE OR REPLACE FUNCTION expire_beta_tester_mappings()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM beta_tester_devices
    WHERE updated_at < NOW() - INTERVAL '90 days';
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;
