# MCP Configuration Flow (SDK + CLI)

## Migration order

1. `001_create_app_logs.sql`
2. `002_create_app_metrics.sql`
3. `003_create_indexes.sql`
4. `004_rls_policies.sql`
5. `005_retention_policy.sql`
6. `006_harden_authenticated_read_policies.sql`
7. `007_add_environment_anomaly_type.sql` — agrega `environment` y `anomaly_type` top-level en `app_logs`
8. `008_add_metrics_environment.sql` — agrega `environment` top-level en `app_metrics`
9. `009_add_missing_indexes.sql` — índices adicionales de performance

10. `010_session_variant.sql` — columna `variant` para A/B testing en `app_logs`
11. `011_batch_integrity.sql` — tabla `log_batches` + columna `batch_id` en `app_logs` (batch integrity)
12. `012_enterprise_indexes_views.sql` — GIN indexes on JSONB, analytics views
13. `013_device_remote_config.sql` — remote config per device (remote debug control)
14. `014_beta_tester_correlation.sql` — beta tester email auto-correlation trigger
15. `015_add_client_timestamp.sql` — columna timestamp BIGINT en app_logs (HMAC)
16. `016_add_metrics_user_id.sql` — columna user_id en app_metrics (GDPR)
17. `017_apploggers_schema.sql` — esquema operacional `apploggers` con vistas para CLI y service_role
18. `018_atomic_log_batch_ingest.sql` — ingestión atómica de logs y control de `batch_id`
19. `019_metric_batch_integrity_and_key_versioning.sql` — manifests de métricas + `key_id` para rotación
20. `020_security_advisor_hardening.sql` — hardening requerido por advisors de seguridad
21. `021_app_identity_source_columns.sql` — `app_package`, `source_scope`, `source_file`, `source_method` top-level

> **Nota sobre numeración**: las migraciones 010–014 se renumeraron para reflejar el orden correcto de aplicación:
>
> | # | Archivo | Descripción |
> |---|---------|-------------|
> | 010 | `010_session_variant.sql` | `session_variant` — columna `variant VARCHAR(100)` en app_logs para A/B testing |
> | 011 | `011_batch_integrity.sql` | `batch_integrity` — tabla `log_batches` + columna `batch_id` en app_logs + RLS para SDK/CLI |
> | 012 | `012_enterprise_indexes_views.sql` | `enterprise_indexes_views` — índices GIN, vistas analíticas, trace_id |
> | 013 | `013_device_remote_config.sql` | `device_remote_config` — tabla device_remote_config + RLS + CHECK constraints |
> | 014 | `014_beta_tester_correlation.sql` | `beta_tester_correlation` — tabla beta_tester_devices + trigger auto-correlación |
> | 015 | `015_add_client_timestamp.sql` | `add_client_timestamp` — columna timestamp BIGINT en app_logs (HMAC) |
> | 016 | `016_add_metrics_user_id.sql` | `add_metrics_user_id` — columna user_id en app_metrics (GDPR) |

## MCP tool names (Supabase MCP)

Use these exact function names when operating via MCP:

| Acción | Función MCP |
|--------|-------------|
| Listar migraciones aplicadas | `mcp_supabase_list_migrations` |
| Listar tablas existentes | `mcp_supabase_list_tables` |
| Aplicar migración DDL | `mcp_supabase_apply_migration` |
| Ejecutar SQL diagnóstico / DML puntual | `mcp_supabase_execute_sql` |
| Ejecutar advisor de seguridad | `mcp_supabase_get_advisors` con `type=security` |
| Ejecutar advisor de performance | `mcp_supabase_get_advisors` con `type=performance` |

### Workflow MCP paso a paso

1. `mcp_supabase_list_migrations` → identificar cuál es la última migración aplicada.
2. Leer el archivo SQL de cada migración faltante desde `docs/ES/migraciones/`.
3. `mcp_supabase_apply_migration` con nombre snake_case y el contenido completo del archivo.
4. Repetir en orden estricto hasta llegar a `021_app_identity_source_columns.sql`.
5. `mcp_supabase_list_tables` → confirmar que existen: `app_logs`, `app_metrics`, `log_batches`, `metric_batches`, `device_remote_config`, `beta_tester_devices`.
7. `mcp_supabase_get_advisors` → revisar y documentar hallazgos.

> Si la migración ya fue aplicada manualmente (columna/tabla ya existe), el SQL usa `IF NOT EXISTS` — es seguro re-ejecutar.

`apploggers` es ahora el schema operativo preferido para CLI y agentes. No requiere una key nueva: se usan las mismas credenciales anon/service_role después de aplicar 017 y exponer correctamente el schema.

## Verification checklist

1. `mcp_supabase_list_migrations` includes all expected steps.
2. `mcp_supabase_list_tables` shows `app_logs`, `app_metrics`, `log_batches`, `device_remote_config`, `beta_tester_devices`.
3. `mcp_supabase_list_tables` or direct SQL confirms top-level columns `app_package`, `source_scope`, `source_file`, `source_method` in logs and metrics.
4. RLS exists and is aligned with operational model.
5. Security advisors reviewed and recorded.
6. Performance advisors reviewed and recorded.
7. `environment` column exists in `app_logs` (migration 007) and `app_metrics` (migration 008).
8. `device_id` column exists in `app_logs` (migration 001) and `app_metrics` (migration 002).
9. `batch_id` column exists in `app_logs` (migration 011).
10. `timestamp BIGINT` column exists in `app_logs` (migration 015).

## Troubleshooting: environment = null en Supabase

**Causa**: La columna `environment` no existe en la tabla porque la migración correspondiente no fue aplicada.

- `app_logs.environment` → migration **007** (`007_add_environment_anomaly_type.sql`)
- `app_metrics.environment` → migration **008** (`008_add_metrics_environment.sql`)
- `log_batches.environment` → migration **011** (`011_batch_integrity.sql`)

El SDK **siempre** envía `environment` en el payload JSON (default: `"production"`). Si la columna no existe, Supabase/PostgREST ignora silenciosamente el campo y almacena NULL.

**Fix**: Aplicar las migraciones faltantes vía `mcp_supabase_apply_migration`.

## Troubleshooting: device_id vs device_fingerprint

Existen **dos identificadores de dispositivo** diferentes — es esencial no confundirlos:

| Identificador | Columna | Origen | Vacío posible |
|---|---|---|---|
| `device_id` | Top-level en `app_logs` y `app_metrics` | UUID v5 derivado de `platform+brand+model+osVersion+apiLevel+appVersion+appBuild` | **Nunca** — siempre genera un UUID válido |
| `device_fingerprint` | `extra->>'device_fingerprint'` (JSONB) | SHA-256(`ANDROID_ID:package_name`) | **Sí** — en emuladores, factory reset reciente, o dispositivos sin `ANDROID_ID` |

Si en Supabase se ve `device_id` vacío en `app_logs`, verificar:
1. Migration 001 aplicada (columna existe).
2. El SDK fue inicializado antes de emitir eventos (si no, la columna llega vacía).

Si `extra->>'device_fingerprint'` aparece vacío string `""`:
- Es normal en emuladores donde `ANDROID_ID = null`.
- Es normal en dispositivos recientemente reseteados a fábrica.
- El `device_id` top-level **no se ve afectado** — siempre tiene un valor.
- El remote config por fingerprint no funcionará si el fingerprint es `""` — usar regla global (fingerprint = NULL) como fallback.

## Troubleshooting: log_batches siempre vacío

**Causa**: La tabla `log_batches` solo recibe filas cuando el SDK tiene `integritySecret` configurado.

Por defecto, `integritySecret` es `""` (blank) → batch integrity **desactivada** → `log_batches` siempre vacío.

**Para activar**:
1. Generar un secret: `apploggers init --generate-integrity-secret`
2. Almacenarlo en `local.properties` como `APPLOGGER_INTEGRITY_SECRET=...` (nunca en VCS).
3. Mapearlo a `BuildConfig` y pasarlo al builder: `.integritySecret(BuildConfig.INTEGRITY_SECRET)`.

## Key model

1. SDK: anon key (insert only).
2. CLI: service_role key (read operations).

## Non-MCP tasks

1. Export env vars in CI/OS.
2. Store secrets in vault.
3. Rotate service_role key with ops process.
