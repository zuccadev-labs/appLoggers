# Local GitHub Actions Validation

Use local `act` validation when workflow-related files changed or when CI parity matters.

Typical commands from repo root:

1. `act push -W .github/workflows/ci.yml --job lint`
2. `act push -W .github/workflows/ci.yml --job test`

Notes:

1. `act` uses `.actrc`.
2. Secrets come from `.act.secrets` based on `.act.secrets.example`.
3. `e2e` may require additional Supabase secrets and is not always mandatory for local verification.
4. On Windows hosts, `act` may lose the Unix execute bit on `sdk/gradlew` when copying from NTFS into Linux containers. Workflows should run `chmod +x ./gradlew` before the first Gradle invocation.
5. `actions/upload-artifact` and related artifact steps can fail under `act` because `ACTIONS_RUNTIME_TOKEN` is not available locally.
6. CodeQL analysis depends on GitHub-hosted API context and is expected to remain partial or unavailable under local `act` runs.
