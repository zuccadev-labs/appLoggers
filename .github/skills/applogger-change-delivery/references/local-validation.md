# Local Validation

Choose validation by change type.

## Profile A: Code Changes (SDK or executable logic)

Run baseline local validation:

1. `cd sdk && ./gradlew check`
2. `cd sdk && ./gradlew assemble`

Push-gate validation:

1. `.githooks/pre-push` runs Detekt.
2. `.githooks/pre-push` runs `:logger-core:jvmTest` and `:logger-test:jvmTest`.

Use the exact pre-push hook when you need parity with local gatekeeping.

## Profile B: Docs-Only Changes

When no executable code changed, skip manual build/test runs.

Run only documentation-focused checks, for example:

1. Verify modified docs render and links are valid.
2. Verify examples and command snippets match current behavior.

If a push still triggers repository hooks or CI checks, allow them to run naturally, but do not add extra manual build/test commands.

## Profile C: Mixed Changes

Apply Profile A for code validation plus docs checks from Profile B.
