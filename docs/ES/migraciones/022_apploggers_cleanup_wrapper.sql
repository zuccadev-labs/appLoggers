-- Migration: 022_apploggers_cleanup_wrapper.sql
-- Descripción: Expone la limpieza operativa también en el schema `apploggers`
-- y normaliza el job de pg_cron para usar la ruta operativa preferida.

CREATE SCHEMA IF NOT EXISTS apploggers;

CREATE OR REPLACE FUNCTION apploggers.purge_old_logs(retention_days INTEGER DEFAULT 30)
RETURNS INTEGER
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.purge_old_logs(retention_days);
$$;

COMMENT ON FUNCTION apploggers.purge_old_logs(INTEGER) IS
'Wrapper operativo sobre public.purge_old_logs(). Purga los datos base visibles desde public y apploggers porque apploggers expone vistas sobre las tablas canónicas.';

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