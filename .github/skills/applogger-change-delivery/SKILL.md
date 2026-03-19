---
name: applogger-change-delivery
description: 'Finish a feature, fix, or refactor professionally in AppLoggers. Use when work is complete and you need local validation, optional local GitHub Actions with act, push to dev, PR to main, and safe merge readiness.'
argument-hint: 'Describe the completed feature, fix, or refactor and whether code, docs, CI, or release files changed.'
user-invocable: true
---

# AppLoggers Change Delivery

## When to Use

Use this skill when a feature, fix, refactor, or docs change is finished and needs repository delivery.

Trigger phrases include:

1. `finish this feature`
2. `close this fix`
3. `prepare the PR`
4. `run the full delivery flow`

## Hard Rules

1. Commit only when the change is complete enough to deliver.
2. Final push for integration goes to `dev`, not `main`.
3. Before pushing, run local validation relevant to the changed area.
4. If CI behavior changed, validate with local `act` when practical.
5. Review docs impact before push.
6. Do not assume every merged PR should become a release tag.
7. Treat merge-to-main and tag creation as separate decisions.
8. Do not push for every small edit; push when a meaningful milestone is complete (full feature slice, completed docs package, or explicit user checkpoint).
9. For docs-only changes, skip manual build/test execution and run documentation-focused checks only.

## Procedure

1. Read [completion checklist](./references/completion-checklist.md).
2. Classify the change as code, docs-only, or mixed.
3. Run [local validation](./references/local-validation.md) using the profile that matches the change class.
4. Run [local GitHub Actions validation](./references/local-actions-validation.md) only when workflow-sensitive.
5. Audit docs with [documentation gate](./references/documentation-gate.md).
6. Commit only after the feature/fix/refactor/docs milestone is complete.
7. Push the integrated milestone to `dev`.
8. Open PR from `dev` to `main`.
9. Merge only after checks are green.
10. Decide whether the merged change is release-tag eligible before invoking the release flow.

## Parallelization Guidance

For larger changes, run read-only subagent passes in parallel before editing or pushing:

1. Code impact scan.
2. Docs/changelog impact scan.
3. CI/workflow impact scan.

## Output Standard

1. Report local validation results.
2. Report the selected validation profile (code, docs-only, or mixed).
3. Report whether docs changed or were intentionally unchanged.
4. Report push/PR status.
5. State whether the change is tag-eligible, not tag-eligible, or needs explicit release intent.