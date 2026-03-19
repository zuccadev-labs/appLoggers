-- Migration: 004_rls_policies.sql
-- Descripción: Row Level Security para Supabase
--
-- TABLA DE ROLES Y PERMISOS:
--
--  Rol           | Llave Supabase         | INSERT | SELECT | Caso de Uso
-- ---------------+------------------------+--------+--------+------------------------------
--  anon          | anon / publishable key |  ✅    |  ❌    | SDK móvil (solo escribe logs)
--  service_role  | service_role key       |  ✅    |  ✅    | CLI de monitoreo / dashboard
--  authenticated | JWT de usuario         |  ✅    |  ✅    | App de monitoreo con auth
--
-- ⚠️  CRÍTICO para el CLI de AppLogger:
--    APPLOGGER_SUPABASE_KEY debe ser la SERVICE ROLE KEY (no la anon key).
--    La anon key no tiene permiso SELECT por diseño (privacidad de logs).
--    La service_role key bypasses RLS y permite queries de lectura.
--
-- ⚠️  CRÍTICO para el SDK móvil:
--    El SDK solo debe usar la ANON KEY.
--    Nunca exponer la service_role key en código de cliente.

-- Habilitar RLS en ambas tablas
ALTER TABLE app_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_metrics ENABLE ROW LEVEL SECURITY;

-- ── SDK (anon key): solo INSERT ────────────────────────────────────────────
-- El SDK móvil usa la llave pública (anon) y solo puede insertar eventos.
CREATE POLICY sdk_insert_logs ON app_logs
    FOR INSERT
    TO anon
    WITH CHECK (true);

CREATE POLICY sdk_insert_metrics ON app_metrics
    FOR INSERT
    TO anon
    WITH CHECK (true);

-- ── CLI / Dashboard (service_role key): lectura completa ───────────────────
-- El CLI de AppLogger y el dashboard de monitoreo usan la service_role key.
-- NOTA: service_role bypasses RLS, estas policies son documentación explícita.
CREATE POLICY monitor_read_logs ON app_logs
    FOR SELECT
    TO service_role
    USING (true);

CREATE POLICY monitor_read_metrics ON app_metrics
    FOR SELECT
    TO service_role
    USING (true);

-- ── Usuarios autenticados (opcional, no habilitado por defecto) ────────────
-- Por seguridad multi-tenant, no se habilita SELECT global para authenticated.
-- Si tu producto requiere lectura por usuario JWT, crea una policy restrictiva
-- basada en claims y ownership (por ejemplo session scope o relación explícita).
