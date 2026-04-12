-- Migration: 006_harden_authenticated_read_policies.sql
-- Descripcion: Endurece RLS eliminando lectura global para authenticated
-- Contexto: si una version anterior agrego policies authenticated_read_*,
--           esta migracion las elimina para evitar exposicion de datos.

DROP POLICY IF EXISTS authenticated_read_logs ON app_logs;
DROP POLICY IF EXISTS authenticated_read_metrics ON app_metrics;
