-- Migration: 017_apploggers_schema.sql
-- Descripción: Expone un schema operacional `apploggers` para CLI/service_role
-- sin mover los datos productivos fuera de `public`.

CREATE SCHEMA IF NOT EXISTS apploggers;

COMMENT ON SCHEMA apploggers IS
'Schema operativo de AppLoggers para lecturas CLI/service_role y segmentación de perfil PostgREST.';

CREATE OR REPLACE VIEW apploggers.app_logs AS
SELECT * FROM public.app_logs;

CREATE OR REPLACE VIEW apploggers.app_metrics AS
SELECT * FROM public.app_metrics;

CREATE OR REPLACE VIEW apploggers.log_batches AS
SELECT * FROM public.log_batches;

CREATE OR REPLACE VIEW apploggers.device_remote_config AS
SELECT * FROM public.device_remote_config;

CREATE OR REPLACE VIEW apploggers.beta_tester_devices AS
SELECT * FROM public.beta_tester_devices;

GRANT USAGE ON SCHEMA apploggers TO service_role;
GRANT ALL ON ALL TABLES IN SCHEMA apploggers TO service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA apploggers GRANT ALL ON TABLES TO service_role;