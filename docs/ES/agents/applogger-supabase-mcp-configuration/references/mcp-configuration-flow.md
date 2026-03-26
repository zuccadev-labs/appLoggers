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

10. `010_log_batches.sql` — manifest table for batch integrity
11. `011_add_variant_column.sql` — session variant / A/B test column
12. `012_enterprise_indexes_views.sql` — GIN indexes on JSONB, analytics views
13. `013_device_remote_config.sql` — remote config per device (remote debug control)
14. `014_beta_tester_correlation.sql` — beta tester email auto-correlation trigger

## Verification checklist

1. `mcp_supabase_list_migrations` includes all expected steps.
2. `mcp_supabase_list_tables` shows `app_logs`, `app_metrics`, `log_batches`, `device_remote_config`, `beta_tester_devices`.
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
