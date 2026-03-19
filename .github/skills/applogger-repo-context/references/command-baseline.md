# Command Baseline

Local validation:

1. `cd sdk && ./gradlew check`
2. `cd sdk && ./gradlew assemble`
3. `sh .githooks/pre-push` from Git for Windows bash when testing the exact push gate.

Local GitHub Actions with `act`:

1. `act push -W .github/workflows/ci.yml --job lint`
2. `act push -W .github/workflows/ci.yml --job test`

Release:

1. `git checkout main`
2. `git pull origin main`
3. `git tag -a vX.Y.Z -m "Release X.Y.Z"`
4. `git push origin vX.Y.Z`