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

9. `009_add_missing_indexes.sql` — índices adicionales de performance

## Verification checklist

1. `mcp_supabase_list_migrations` includes all expected steps.
2. `mcp_supabase_list_tables` shows `app_logs` and `app_metrics`.
3. RLS exists and is aligned with operational model.
4. Security advisors reviewed and recorded.
5. Performance advisors reviewed and recorded.

## Key model

1. SDK: anon key (insert only).
2. CLI: service_role key (read operations).

## Non-MCP tasks

1. Export env vars in CI/OS.
2. Store secrets in vault.
3. Rotate service_role key with ops process.
