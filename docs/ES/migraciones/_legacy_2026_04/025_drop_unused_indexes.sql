-- Migration: 025_drop_unused_indexes.sql
-- Descripcion: Elimina indices sin uso que no respaldan rutas operativas
-- actuales del CLI, SDK o jobs de verificacion.

DROP INDEX IF EXISTS apploggers.idx_app_logs_platform;
DROP INDEX IF EXISTS apploggers.idx_app_metrics_tags_gin;