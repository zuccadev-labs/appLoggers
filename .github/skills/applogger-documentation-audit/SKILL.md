---
name: applogger-documentation-audit
description: 'Audit and update AppLoggers documentation after features, fixes, refactors, release changes, and dependency updates. Use when code changed and docs, README, changelog, or integration guides might now be stale.'
argument-hint: 'Describe the completed change and which modules or workflows were affected.'
user-invocable: true
---

# AppLoggers Documentation Audit

## When to Use

Use this skill after any change that may affect usage, setup, workflow, release, or architecture docs.

Trigger phrases include:

1. `audit the docs`
2. `update README and changelog`
3. `check if documentation drift exists`

## Hard Rules

1. Verify docs against current code and workflows.
2. Prefer factual alignment over marketing language.
3. Do not claim unsupported guarantees.
4. Keep version references accurate.

## Procedure

1. Read [documentation scope](./references/documentation-scope.md).
2. Run [audit checklist](./references/audit-checklist.md).
3. Update any affected usage or release instructions.
4. Recheck branch, CI, and release flow wording if workflow files changed.

## Output Standard

1. List affected docs.
2. State what changed and why.
3. Note any docs intentionally left unchanged.