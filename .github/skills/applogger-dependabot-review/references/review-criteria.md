# Review Criteria

Check:

1. Which files changed.
2. Whether changes are version bumps only or behavior-altering.
3. Whether CI is green.
4. Whether release or publishing behavior could change.
5. Whether the update is targeted to `dev` as configured.

Low-risk examples:

1. GitHub Actions version bumps with green checks.
2. Documentation-only tooling updates.

Higher-risk examples:

1. Kotlin major version changes.
2. AGP or Gradle behavior shifts.
3. Publishing or release-action changes.