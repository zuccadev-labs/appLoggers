-- Migration 005: AppLoggers privileges and scheduled jobs
-- Scope: minimum-privilege grants and maintenance scheduling.

SET search_path = apploggers, pg_catalog;

COMMENT ON SCHEMA apploggers IS
'Schema fisico y operativo de AppLoggers. Todas las tablas, funciones y vistas operativas viven en este namespace.';

REVOKE USAGE ON SCHEMA apploggers FROM authenticated;
GRANT USAGE ON SCHEMA apploggers TO anon, service_role;

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA apploggers FROM anon, authenticated, service_role;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA apploggers FROM anon, authenticated, service_role;
REVOKE ALL PRIVILEGES ON ALL ROUTINES IN SCHEMA apploggers FROM anon, authenticated, service_role;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA apploggers TO service_role;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA apploggers TO service_role;

GRANT INSERT ON apploggers.app_logs TO anon;
GRANT INSERT ON apploggers.app_metrics TO anon;
GRANT INSERT ON apploggers.log_batches TO anon;
GRANT INSERT ON apploggers.metric_batches TO anon;
GRANT SELECT ON apploggers.device_remote_config TO anon;
GRANT SELECT, INSERT, UPDATE ON apploggers.beta_tester_devices TO anon;

GRANT SELECT ON apploggers.session_summary TO service_role;
GRANT SELECT ON apploggers.hourly_error_rate TO service_role;
GRANT SELECT ON apploggers.device_health TO service_role;
GRANT SELECT ON apploggers.hourly_metrics_summary TO service_role;

GRANT EXECUTE ON FUNCTION apploggers.ingest_log_batch(JSONB, JSONB) TO anon, service_role;
GRANT EXECUTE ON FUNCTION apploggers.ingest_metric_batch(JSONB, JSONB) TO anon, service_role;
GRANT EXECUTE ON FUNCTION apploggers.purge_old_logs(INTEGER) TO service_role;
GRANT EXECUTE ON FUNCTION apploggers.expire_beta_tester_mappings() TO service_role;
GRANT EXECUTE ON FUNCTION apploggers.update_device_config_timestamp() TO service_role;
GRANT EXECUTE ON FUNCTION apploggers.correlate_beta_tester_email() TO service_role;

ALTER DEFAULT PRIVILEGES IN SCHEMA apploggers REVOKE ALL ON TABLES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA apploggers REVOKE ALL ON SEQUENCES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA apploggers REVOKE EXECUTE ON FUNCTIONS FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA apploggers GRANT ALL ON TABLES TO service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA apploggers GRANT ALL ON SEQUENCES TO service_role;

DO $$
DECLARE
    purge_job_id BIGINT;
    beta_cleanup_job_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        SELECT jobid INTO purge_job_id
        FROM cron.job
        WHERE jobname = 'purge-old-logs-every-3-days'
        LIMIT 1;

        IF purge_job_id IS NOT NULL THEN
            PERFORM cron.unschedule(purge_job_id);
        END IF;

        PERFORM cron.schedule(
            'purge-old-logs-every-3-days',
            '0 3 */3 * *',
            'select apploggers.purge_old_logs(3);'
        );

        SELECT jobid INTO beta_cleanup_job_id
        FROM cron.job
        WHERE jobname = 'expire-beta-tester-mappings-weekly'
        LIMIT 1;

        IF beta_cleanup_job_id IS NOT NULL THEN
            PERFORM cron.unschedule(beta_cleanup_job_id);
        END IF;

        PERFORM cron.schedule(
            'expire-beta-tester-mappings-weekly',
            '0 4 * * 0',
            'select apploggers.expire_beta_tester_mappings();'
        );
    END IF;
END;
$$;
