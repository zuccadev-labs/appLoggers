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

## Workflow

1. Read migration files under `docs/ES/migraciones`.
2. Inspect remote migration state via MCP.
3. Apply missing migrations in order.
4. If conflicts exist (existing policy/index), switch to idempotent migration strategy.
5. Validate final state:
   - migrations list
   - tables list
   - RLS policy expectations
6. Run advisors (security and performance) and report remediations.

## Expected backend contract

1. `app_logs` and `app_metrics` tables exist.
2. `log_batches` table exists (batch integrity manifests).
3. `device_remote_config` table exists (remote debug control per device).
4. `beta_tester_devices` table exists (auto-correlation of tester emails).
5. RLS enabled in all tables.
6. anon can insert via `sdk_insert_*` policies.
7. anon can SELECT `device_remote_config` (enabled rows only).
8. service_role can read/write via `monitor_read_*` / `service_all` policies.
9. `authenticated_read_*` global policies are absent by default.
10. Trigger `trg_correlate_beta_tester` on `app_logs` auto-fills beta tester email.

## Gaps outside MCP

1. service_role key provisioning/rotation.
2. CI/OS secret injection.
3. local.properties edits in end-user workstation.

## References bundled with this skill

1. `references/mcp-configuration-flow.md`

## Output standard

1. Report what was applied vs already present.
2. Report detected drift and normalization actions.
3. Report residual risks and non-MCP follow-ups.
4. Include a final go/no-go readiness verdict.
