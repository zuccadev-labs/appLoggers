-- Migration 001: AppLoggers core schema
-- Scope: foundational tables, constraints, and structural integrity.

CREATE SCHEMA IF NOT EXISTS apploggers;
SET search_path = apploggers, pg_catalog;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS apploggers.log_batches (
    batch_id UUID PRIMARY KEY,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_count INT NOT NULL CHECK (event_count > 0),
    batch_hash TEXT NOT NULL DEFAULT '' CHECK (CHAR_LENGTH(batch_hash) > 0),
    environment VARCHAR(50) NULL
        CHECK (environment IS NULL OR environment IN ('development', 'staging', 'production', 'test')),
    sdk_version VARCHAR(20) NULL,
    key_id VARCHAR(64) NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS apploggers.metric_batches (
    batch_id UUID PRIMARY KEY,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_count INT NOT NULL CHECK (event_count > 0),
    batch_hash TEXT NOT NULL DEFAULT '' CHECK (CHAR_LENGTH(batch_hash) > 0),
    environment VARCHAR(50) NULL
        CHECK (environment IS NULL OR environment IN ('development', 'staging', 'production', 'test')),
    sdk_version VARCHAR(20) NULL,
    key_id VARCHAR(64) NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS apploggers.app_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    level VARCHAR(10) NOT NULL CHECK (level IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL')),
    tag VARCHAR(100) NOT NULL DEFAULT '',
    message TEXT NOT NULL,
    throwable_type VARCHAR(200) NULL,
    throwable_msg TEXT NULL,
    stack_trace TEXT[] NULL,
    device_info JSONB NOT NULL DEFAULT '{}',
    api_level INTEGER NOT NULL DEFAULT 0,
    sdk_version VARCHAR(20) NOT NULL DEFAULT '0.0.0',
    session_id TEXT NOT NULL,
    device_id TEXT NOT NULL DEFAULT '',
    user_id TEXT NULL,
    app_package TEXT NULL,
    source_scope TEXT NULL,
    source_file TEXT NULL,
    source_method TEXT NULL,
    extra JSONB NULL,
    environment VARCHAR(50) NULL CHECK (environment IS NULL OR environment IN ('development', 'staging', 'production', 'test')),
    anomaly_type VARCHAR(100) NULL,
    variant VARCHAR(100) NULL,
    trace_id TEXT NULL,
    timestamp BIGINT NULL,
    batch_id UUID NULL REFERENCES apploggers.log_batches(batch_id) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE IF NOT EXISTS apploggers.app_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    name VARCHAR(100) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(20) NOT NULL DEFAULT 'count',
    tags JSONB NOT NULL DEFAULT '{}',
    environment VARCHAR(50) NULL CHECK (environment IS NULL OR environment IN ('development', 'staging', 'production', 'test')),
    device_id TEXT NOT NULL DEFAULT '',
    session_id TEXT NOT NULL,
    sdk_version VARCHAR(20) NOT NULL DEFAULT '0.0.0',
    user_id TEXT NULL,
    timestamp BIGINT NULL,
    batch_id UUID NULL REFERENCES apploggers.metric_batches(batch_id) DEFERRABLE INITIALLY DEFERRED,
    app_package TEXT NULL,
    source_scope TEXT NULL,
    source_file TEXT NULL,
    source_method TEXT NULL
);

CREATE TABLE IF NOT EXISTS apploggers.device_remote_config (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    device_fingerprint TEXT,
    environment VARCHAR(50),
    min_level VARCHAR(20) DEFAULT 'ERROR' CHECK (min_level IN ('DEBUG', 'INFO', 'WARN', 'ERROR', 'CRITICAL')),
    debug_enabled BOOLEAN DEFAULT false,
    tags_allow TEXT[],
    tags_block TEXT[],
    sampling_rate DOUBLE PRECISION DEFAULT 1.0 CHECK (sampling_rate >= 0.0 AND sampling_rate <= 1.0),
    enabled BOOLEAN DEFAULT true,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS apploggers.beta_tester_devices (
    device_id TEXT PRIMARY KEY,
    email TEXT NOT NULL,
    app_package TEXT,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
