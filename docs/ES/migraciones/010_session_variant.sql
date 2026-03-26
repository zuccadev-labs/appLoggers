-- Migration 010: session variant column for A/B testing
ALTER TABLE app_logs ADD COLUMN IF NOT EXISTS variant VARCHAR(100) NULL;
CREATE INDEX IF NOT EXISTS idx_app_logs_variant ON app_logs (variant) WHERE variant IS NOT NULL;
