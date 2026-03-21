---
name: applogger-cli-agent-operator
description: 'Operate the AppLoggers CLI as a deterministic interface for AI agents and automation. Use this when an agent must query CLI metadata, capabilities, health, and telemetry output in machine-readable form.'
argument-hint: 'Describe what automation or agent task should be executed through applogger-cli and which output contract is required.'
user-invocable: true
---

# AppLogger CLI Agent Operator

## Purpose

Use this skill when a Copilot agent or automation needs to interact with AppLoggers through the CLI contract in a stable and parseable way.

## Hard Rules

1. Before telemetry usage, ensure the CLI is installed and executable on the current host.
2. Prefer `--output agent` for machine consumption (TOON compact encoding via toon-go).
3. Use `--output json` when downstream systems require JSON strictly.
4. Treat exit code 0 as success, 1 as runtime failure, 2 as usage error.
5. Run `agent schema` and `capabilities` before implementing new automation assumptions.
6. Do not parse free-form text when JSON is available.

## Installation Bootstrap

If `applogger-cli` is not yet available, install it using the host-native one-line bootstrap:

1. Linux:
   - `curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash`
2. macOS:
   - `curl -fsSL https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.sh | bash`
3. Windows PowerShell:
   - `irm https://raw.githubusercontent.com/devzucca/appLoggers/main/cli/install/install.ps1 | iex`

Bootstrap rules:

1. Verify install by running `applogger-cli version --output json`.
2. If `PATH` changed during installation, start a new shell or invoke the installed binary by absolute path once.
3. To pin a specific version, set `APPLOGGER_CLI_VERSION=applogger-cli-vX.Y.Z` before invoking the installer.
4. On macOS/Linux, the bash installer validates SHA-256 checksums for release assets when verification tools are available.

## Standard Command Set

0. Installation verification:
   - `applogger-cli version --output json`
1. Metadata discovery:
   - `applogger-cli --syncbin-metadata --output json`
2. Version/build discovery:
   - `applogger-cli version --output json`
3. Capability discovery:
   - `applogger-cli capabilities --output agent`
4. Agent contract discovery:
   - `applogger-cli agent schema --output agent`
5. Runtime readiness probe:
   - `applogger-cli health --output agent`
6. Telemetry query endpoint:
   - `applogger-cli telemetry query --output agent`
7. Dedicated compact orchestration response:
   - `applogger-cli telemetry agent-response --source logs --aggregate severity --preview-limit 5`
8. Warning anomaly inspection:
   - `applogger-cli telemetry query --source logs --severity warn --anomaly-type slow_response --output json`

## Supabase Environment Setup

Before `telemetry query`, ensure environment is configured:

1. Preferred corporate mode (multi-project):
   - `APPLOGGER_CONFIG` (shared JSON project registry)
   - `APPLOGGER_PROJECT` (explicit project selection, optional)
   - `--config` and `--project` flags for deterministic overrides
2. Legacy required (fallback mode):
   - `appLogger_supabaseUrl`
   - `appLogger_supabaseKey` (service_role key)
3. Optional:
   - `appLogger_supabaseSchema`
   - `appLogger_supabaseLogTable`
   - `appLogger_supabaseMetricTable`
   - `appLogger_supabaseTimeoutSeconds`
4. If operating with Supabase MCP available, retrieve:
   - project URL from `mcp_supabase_get_project_url`
5. Provision `appLogger_supabaseKey` from secure secret storage (service_role).
   - Do not use publishable/anon keys for CLI read operations.

Project-resolution precedence for automation:

1. `--project`
2. `APPLOGGER_PROJECT`
3. Workspace autodetection via `workspace_roots`
4. `default_project`
5. Single configured project
6. Legacy env fallback (`appLogger_supabase*`, `APPLOGGER_SUPABASE_*`, `SUPABASE_*`)

Auditability rule:

1. Persist `project` and `config_source` from health/telemetry outputs in agent logs.

Telemetry notes:

1. Log rows may include `extra` with `extra.anomaly_type`.
2. Use `--anomaly-type` only with `--source=logs`.

## Error Handling Contract

1. If exit code is 2, the caller should correct arguments and retry.
2. If exit code is 1, treat as runtime error and escalate with captured stderr JSON envelope.
3. Always store command, args, exit code, and full JSON response in agent logs for traceability.

## Output Standard

1. Report executed commands.
2. Report parsed JSON fields used for decisions.
3. If querying logs, state whether `extra` or `extra.anomaly_type` influenced the decision.
4. Report retries and final status.
5. Report remaining uncertainty if command is preview status.
6. If installation was required, report install source, resolved version tag, detected OS/arch, and installed path.
