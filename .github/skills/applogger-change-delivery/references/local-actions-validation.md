# Local GitHub Actions Validation

Use local `act` validation when workflow-related files changed or when CI parity matters.

Typical commands from repo root:

1. `act push -W .github/workflows/ci.yml --job lint`
2. `act push -W .github/workflows/ci.yml --job test`

Notes:

1. `act` uses `.actrc`.
2. Secrets come from `.act.secrets` based on `.act.secrets.example`.
3. `e2e` may require additional Supabase secrets and is not always mandatory for local verification.