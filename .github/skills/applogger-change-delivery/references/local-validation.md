# Local Validation

Baseline local validation:

1. `cd sdk && ./gradlew check`
2. `cd sdk && ./gradlew assemble`

Push-gate validation:

1. `.githooks/pre-push` runs Detekt.
2. `.githooks/pre-push` runs `:logger-core:jvmTest` and `:logger-test:jvmTest`.

Use the exact pre-push hook when you need parity with local gatekeeping.