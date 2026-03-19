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

## Procedure

1. Read [completion checklist](./references/completion-checklist.md).
2. Run [local validation](./references/local-validation.md).
3. Run [local GitHub Actions validation](./references/local-actions-validation.md) when workflow-sensitive.
4. Audit docs with [documentation gate](./references/documentation-gate.md).
5. Commit only after the feature/fix/refactor is complete.
6. Push the integrated change to `dev`.
7. Open PR from `dev` to `main`.
8. Merge only after checks are green.

## Parallelization Guidance

For larger changes, run read-only subagent passes in parallel before editing or pushing:

1. Code impact scan.
2. Docs/changelog impact scan.
3. CI/workflow impact scan.

## Output Standard

1. Report local validation results.
2. Report whether docs changed or were intentionally unchanged.
3. Report push/PR status.
4. State whether the change is ready for tag flow or not.