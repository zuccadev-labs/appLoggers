-- Migration: 017_apploggers_schema.sql
-- Descripción: Consolida el schema operacional `apploggers` como único namespace.

CREATE SCHEMA IF NOT EXISTS apploggers;
SET search_path = apploggers, pg_catalog;

COMMENT ON SCHEMA apploggers IS
'Schema operativo de AppLoggers para CLI/service_role y segmentación de perfil PostgREST.';

GRANT USAGE ON SCHEMA apploggers TO service_role;
GRANT ALL ON ALL TABLES IN SCHEMA apploggers TO service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA apploggers GRANT ALL ON TABLES TO service_role;