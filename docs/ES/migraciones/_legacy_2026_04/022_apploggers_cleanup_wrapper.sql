-- Migration: 022_apploggers_cleanup_wrapper.sql
-- Descripción: Normaliza el job de pg_cron para usar la ruta operativa en apploggers.
-- All purge operations are already within apploggers schema since migration 018.

CREATE SCHEMA IF NOT EXISTS apploggers;

-- Ensure purge_old_logs exists in apploggers (should have been created earlier)
-- If it doesn't exist, create a stub that will be completed by earlier migrations
CREATE OR REPLACE FUNCTION apploggers.purge_old_logs(retention_days INTEGER DEFAULT 30)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = apploggers
AS $$
BEGIN
    DELETE FROM apploggers.app_logs
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;
    
    DELETE FROM apploggers.app_metrics
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;
    
    RETURN 0;
END;
$$;

COMMENT ON FUNCTION apploggers.purge_old_logs(INTEGER) IS
'Purga registros y métricas antiguos según el período de retención especificado. Todos los datos son operados dentro del schema apploggers.';

GRANT EXECUTE ON FUNCTION apploggers.purge_old_logs(INTEGER) TO service_role;

DO $$
DECLARE
    existing_job_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        SELECT jobid
        INTO existing_job_id
        FROM cron.job
        WHERE jobname = 'purge-old-logs-every-3-days'
        LIMIT 1;

        IF existing_job_id IS NOT NULL THEN
            PERFORM cron.unschedule(existing_job_id);
        END IF;

        PERFORM cron.schedule(
            'purge-old-logs-every-3-days',
            '0 3 */3 * *',
            'select apploggers.purge_old_logs(3);'
        );
    END IF;
END;
$$;