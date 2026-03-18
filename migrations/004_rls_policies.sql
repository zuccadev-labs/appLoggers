-- Migration: 004_rls_policies.sql
-- Descripción: Row Level Security para Supabase

-- Habilitar RLS en ambas tablas
ALTER TABLE app_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_metrics ENABLE ROW LEVEL SECURITY;

-- El rol anon (SDK) solo puede insertar
CREATE POLICY sdk_insert_logs ON app_logs
    FOR INSERT
    TO anon
    WITH CHECK (true);

CREATE POLICY sdk_insert_metrics ON app_metrics
    FOR INSERT
    TO anon
    WITH CHECK (true);

-- El service_role (app de monitoreo) puede leer todo
CREATE POLICY monitor_read_logs ON app_logs
    FOR SELECT
    TO service_role
    USING (true);

CREATE POLICY monitor_read_metrics ON app_metrics
    FOR SELECT
    TO service_role
    USING (true);
