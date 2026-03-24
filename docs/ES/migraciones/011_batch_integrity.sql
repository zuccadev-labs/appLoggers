-- Migration 011: batch integrity table and batch_id column on app_logs
CREATE TABLE IF NOT EXISTS log_batches (
    batch_id    UUID PRIMARY KEY,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_count INT NOT NULL,
    batch_hash  TEXT NOT NULL DEFAULT '',
    environment VARCHAR(50) NULL,
    sdk_version VARCHAR(20) NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_log_batches_sent_at ON log_batches (sent_at DESC);
ALTER TABLE app_logs ADD COLUMN IF NOT EXISTS batch_id UUID NULL;
CREATE INDEX IF NOT EXISTS idx_app_logs_batch_id ON app_logs (batch_id) WHERE batch_id IS NOT NULL;
