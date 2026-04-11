---
name: applogger-supabase-mcp-configuration
description: Configure Supabase backend end-to-end for AppLogger SDK and CLI using MCP operations. Use when the user asks to prepare migrations, RLS, and operational validation via MCP.
---

# AppLogger Supabase MCP Configuration

## When to use this skill

Use this skill when the user wants to configure or validate the Supabase backend for both SDK and CLI through MCP tools.

Primary use cases:

1. Apply or repair AppLogger migrations.
2. Validate RLS alignment for SDK writes and CLI reads.
3. Check drift between repository migrations and remote database state.
4. Produce operational evidence (tables, migrations, advisors).

## Mandatory constraints

1. Use migration order strictly.
2. Prefer idempotent SQL where drift is possible.
3. Never expose secrets in logs or outputs.
4. Keep SDK/CLI key model explicit:
   - SDK writes with anon key.
   - CLI reads with service_role key.
5. Use `mcp_supabase_apply_migration` for DDL migrations; reserve `mcp_supabase_execute_sql` for verification queries and tightly scoped DML.

## Workflow

1. Read migration files under `docs/ES/migraciones`.
2. Inspect remote migration state via MCP.
3. Apply missing migrations in order with `mcp_supabase_apply_migration`.
4. If conflicts exist (existing policy/index), switch to idempotent migration strategy.
5. Validate final state:
   - migrations list
   - tables list
   - RLS policy expectations
6. Run advisors (security and performance) and report remediations.

## Expected backend contract

1. `app_logs` and `app_metrics` tables exist with all columns from migrations 001–021.
2. `log_batches` table exists (batch integrity manifests) — migration 011.
3. `device_remote_config` table exists (remote debug control per device) — migration 013.
4. `beta_tester_devices` table exists (auto-correlation of tester emails) — migration 014.
5. RLS enabled in all tables.
6. anon can insert via `sdk_insert_*` policies.
7. anon can SELECT `device_remote_config` (enabled rows only).
8. service_role can read/write via `monitor_read_*` / `service_all` policies.
9. `authenticated_read_*` global policies are absent by default.
10. Trigger `trg_correlate_beta_tester` on `app_logs` auto-fills beta tester email.
11. `environment` column exists in `app_logs` (migration 007) and `app_metrics` (migration 008) — **si falta, todos los eventos llegan con environment = NULL en Supabase pese a que el SDK envía el valor correctamente**.
12. `device_id` column exists in `app_logs` (migration 001) y `app_metrics` (migration 002).
13. `batch_id` column exists in `app_logs` (migration 011).
14. `timestamp BIGINT` column exists in `app_logs` (migration 015) — requerido para verificación HMAC.
15. `log_batches` es vacío por diseño cuando `integritySecret` no está configurado en el SDK — no es un error de migración.
16. `apploggers` es el schema operativo y fisico obligatorio despues de migration 023; `public` no debe seguir alojando tablas, funciones, triggers ni vistas operativas de AppLoggers.
17. `app_package`, `source_scope`, `source_file` y `source_method` existen como columnas top-level en `app_logs` y `app_metrics` (migration 021).
18. No new key is required for `apploggers`; the same anon/service_role model applies once the schema is exposed correctly.
19. La limpieza/retencion debe existir en `apploggers`: `apploggers.purge_old_logs(...)` debe existir y el job de `pg_cron` debe invocarlo por ese schema.
20. Despues de migration 023, `apploggers` debe contener las tablas base operativas (`BASE TABLE`) y `public` debe quedar fuera del camino operativo de AppLoggers.
21. Despues de migration 024, `authenticated` no debe conservar grants operativos sobre tablas ni `EXECUTE` sobre routines de `apploggers`.
22. Despues de migration 024, `PUBLIC` no debe conservar `EXECUTE` sobre routines de `apploggers`.
23. Solo las RPC `ingest_log_batch` e `ingest_metric_batch` deben quedar expuestas a `anon`; `purge_old_logs` y `expire_beta_tester_mappings` deben quedar expuestas a `service_role`.
24. Produccion debe tener dos jobs activos en `pg_cron`: `purge-old-logs-every-3-days` y `expire-beta-tester-mappings-weekly`, ambos apuntando a `apploggers`.

## Diagnóstico de columnas críticas

Antes de declarar el backend listo, ejecutar estas queries via `mcp_supabase_execute_sql` para confirmar columnas:

```sql
-- Verificar columnas clave en app_logs
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'app_logs'
  AND column_name IN ('environment','anomaly_type','device_id','batch_id','timestamp','variant','trace_id','app_package','source_scope','source_file','source_method')
ORDER BY column_name;

-- Verificar columnas clave en app_metrics
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'app_metrics'
  AND column_name IN ('environment','device_id','user_id','app_package','source_scope','source_file','source_method')
ORDER BY column_name;

-- Verificar columnas de log_batches
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'log_batches'
ORDER BY column_name;
```

Si falta alguna columna, identificar la migración responsable y aplicarla con `mcp_supabase_apply_migration`.

## Diagnóstico de schema operativo y cleanup

Antes de declarar `apploggers` listo al 100%, ejecutar estas verificaciones via `mcp_supabase_execute_sql`:

```sql
-- Verificar que apploggers aloja las tablas operativas fisicas
SELECT table_schema, table_name, table_type
FROM information_schema.tables
WHERE table_schema IN ('public', 'apploggers')
  AND table_name IN ('app_logs','app_metrics','log_batches','metric_batches','device_remote_config','beta_tester_devices')
ORDER BY table_schema, table_name;

-- Verificar que las funciones operativas viven en apploggers
SELECT routine_schema, routine_name, routine_type
FROM information_schema.routines
WHERE routine_schema IN ('public', 'apploggers')
  AND routine_name IN ('purge_old_logs', 'ingest_log_batch', 'ingest_metric_batch', 'correlate_beta_tester_email', 'expire_beta_tester_mappings', 'update_device_config_timestamp')
ORDER BY routine_schema, routine_name;

-- Verificar que los triggers operativos ya no viven en public
SELECT trigger_schema, event_object_table, trigger_name
FROM information_schema.triggers
WHERE trigger_schema IN ('public', 'apploggers')
ORDER BY trigger_schema, event_object_table, trigger_name;

-- Verificar que pg_cron usa la ruta operativa preferida
SELECT jobid, jobname, schedule, command, active
FROM cron.job
WHERE jobname IN ('purge-old-logs-every-3-days', 'expire-beta-tester-mappings-weekly')
ORDER BY jobname;

-- Verificar grants SQL efectivos sobre tablas operativas
SELECT grantee, table_name, privilege_type
FROM information_schema.role_table_grants
WHERE table_schema = 'apploggers'
  AND grantee IN ('anon', 'authenticated', 'service_role')
ORDER BY grantee, table_name, privilege_type;

-- Verificar routines expuestas realmente al API layer
SELECT grantee, routine_name, privilege_type
FROM information_schema.routine_privileges
WHERE routine_schema = 'apploggers'
  AND grantee IN ('PUBLIC', 'anon', 'authenticated', 'service_role')
ORDER BY grantee, routine_name, privilege_type;
```

Resultado esperado:

1. `apploggers` contiene las `BASE TABLE` operativas y `public` no contiene relaciones operativas de AppLoggers.
2. Las funciones operativas viven en `apploggers` y no dependen de wrappers hacia `public`.
3. Los triggers operativos viven en `apploggers`.
4. El job `purge-old-logs-every-3-days` ejecuta `select apploggers.purge_old_logs(3);`.
5. El job `expire-beta-tester-mappings-weekly` ejecuta `select apploggers.expire_beta_tester_mappings();`.
6. `authenticated` no conserva grants de tablas ni `EXECUTE` sobre routines de `apploggers`.
7. `PUBLIC` no conserva `EXECUTE` sobre routines de `apploggers`.
8. `anon` solo conserva los grants minimos de insert/select/update esperados para el SDK.

## Gaps outside MCP

1. service_role key provisioning/rotation.
2. CI/OS secret injection.
3. local.properties edits in end-user workstation.
4. `integritySecret` provisioning — debe generarse con un CSPRNG real (OpenSSL, PowerShell, secret manager de CI/CD) y nunca estar hardcodeado en el APK.

## References bundled with this skill

1. `references/mcp-configuration-flow.md`

## Output standard

1. Report what was applied vs already present.
2. Report detected drift and normalization actions.
3. Report residual risks and non-MCP follow-ups.
4. Include a final go/no-go readiness verdict.
5. Explicar explicitamente si `apploggers` ya es schema fisico real o si aun queda drift hacia `public`.
