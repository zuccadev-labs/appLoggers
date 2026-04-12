-- Migration: 005_retention_policy.sql
-- Descripción: Política de retención automática (purga de logs antiguos)

-- Función de purga: elimina registros más antiguos que N días
CREATE OR REPLACE FUNCTION purge_old_logs(retention_days INTEGER DEFAULT 30)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM app_logs
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    DELETE FROM app_metrics
    WHERE created_at < NOW() - (retention_days || ' days')::INTERVAL;

    RETURN deleted_count;
END;
$$;

-- Programar purga diaria (requiere pg_cron en Supabase)
-- SELECT cron.schedule('purge-old-logs', '0 3 * * *', 'SELECT purge_old_logs(30)');
