-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration 015: Add client-side timestamp column to app_logs
-- ═══════════════════════════════════════════════════════════════════════════════
-- Problem: The SDK's BatchIntegrityManager computes the HMAC canonical string
-- using LogEvent.timestamp (client-side epoch millis), but app_logs only had
-- created_at (server-generated TIMESTAMPTZ). The CLI verify command could never
-- reproduce the same hash because the values differ.
--
-- Fix: Add a BIGINT timestamp column that stores the client-side epoch millis
-- exactly as produced by currentTimeMillis(). The CLI verify command reads this
-- column to recompute the HMAC and compare against the batch manifest hash.
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE app_logs ADD COLUMN IF NOT EXISTS timestamp BIGINT NULL;

COMMENT ON COLUMN app_logs.timestamp IS 'Client-side epoch millis (currentTimeMillis). Used for HMAC batch integrity verification. NULL for events ingested before migration 015.';
