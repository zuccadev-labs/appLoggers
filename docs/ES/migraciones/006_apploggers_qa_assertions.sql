-- Migration 006: AppLoggers QA assertions
-- Scope: deployment-time invariants to fail fast on invalid state.

SET search_path = apploggers, pg_catalog;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'apploggers' AND table_name = 'app_logs' AND table_type = 'BASE TABLE'
    ) THEN
        RAISE EXCEPTION 'qa_assertion_failed: app_logs table missing in apploggers';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'apploggers' AND table_name = 'app_metrics' AND table_type = 'BASE TABLE'
    ) THEN
        RAISE EXCEPTION 'qa_assertion_failed: app_metrics table missing in apploggers';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'apploggers' AND p.proname = 'ingest_log_batch'
    ) THEN
        RAISE EXCEPTION 'qa_assertion_failed: ingest_log_batch missing in apploggers';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'apploggers' AND p.proname = 'ingest_metric_batch'
    ) THEN
        RAISE EXCEPTION 'qa_assertion_failed: ingest_metric_batch missing in apploggers';
    END IF;
END;
$$;
