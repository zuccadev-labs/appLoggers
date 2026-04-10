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
16. `apploggers` is the preferred operational schema after migration 017; `public` remains a legacy compatibility path.
17. `app_package`, `source_scope`, `source_file` y `source_method` existen como columnas top-level en `app_logs` y `app_metrics` (migration 021).
18. No new key is required for `apploggers`; the same anon/service_role model applies once the schema is exposed correctly.
19. La limpieza/retención también debe estar expuesta por `apploggers` después de migration 022: `apploggers.purge_old_logs(...)` debe existir y el job de `pg_cron` debe invocarlo por el schema operativo preferido.
20. Como `apploggers` expone vistas sobre tablas canónicas en `public`, purgar vía `apploggers.purge_old_logs(...)` limpia el mismo dataset visible desde ambos schemas; no se deben crear tablas duplicadas solo para retención.

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
-- Verificar que apploggers expone vistas operativas, no tablas duplicadas
SELECT table_schema, table_name, table_type
FROM information_schema.tables
WHERE table_schema IN ('public', 'apploggers')
  AND table_name IN ('app_logs','app_metrics','log_batches','metric_batches','device_remote_config','beta_tester_devices')
ORDER BY table_schema, table_name;

-- Verificar que la limpieza existe también en apploggers
SELECT routine_schema, routine_name, routine_type
FROM information_schema.routines
WHERE routine_schema IN ('public', 'apploggers')
  AND routine_name = 'purge_old_logs'
ORDER BY routine_schema;

-- Verificar que pg_cron usa la ruta operativa preferida
SELECT jobid, jobname, schedule, command, active
FROM cron.job
WHERE jobname = 'purge-old-logs-every-3-days';
```

Resultado esperado:

1. `public` contiene tablas base y `apploggers` contiene vistas operativas.
2. Existen `public.purge_old_logs(...)` y `apploggers.purge_old_logs(...)`.
3. El job `purge-old-logs-every-3-days` ejecuta `select apploggers.purge_old_logs(3);`.

## Gaps outside MCP

1. service_role key provisioning/rotation.
2. CI/OS secret injection.
3. local.properties edits in end-user workstation.
4. `integritySecret` provisioning — debe generarse con `apploggers init --generate-integrity-secret` y nunca estar hardcodeado en el APK.

## References bundled with this skill

1. `references/mcp-configuration-flow.md`

## Output standard

1. Report what was applied vs already present.
2. Report detected drift and normalization actions.
3. Report residual risks and non-MCP follow-ups.
4. Include a final go/no-go readiness verdict.
5. Explicar explícitamente si `apploggers` limpia datos por wrapper sobre `public` o si hay tablas físicas separadas.
