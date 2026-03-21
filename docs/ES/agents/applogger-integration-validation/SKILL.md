---
name: applogger-integration-validation
description: Validate whether an AppLogger integration is release-ready. Use when the user asks for smoke tests, QA checklist, acceptance gates, and final go/no-go criteria.
---

# AppLogger Integration Validation

## When to use this skill

Use this skill when integration seems complete and needs objective validation.

## Mandatory constraints

1. Validate on real execution path, not only static inspection.
2. Include both functional and operational checks.
3. Verify local configuration handling policy compliance.

## Workflow

1. Run smoke checks.
2. Validate health snapshot evolution.
3. Validate backend event arrival.
4. Collect QA evidence.
5. Decide go/no-go with explicit acceptance gates.

## References bundled with this skill

1. `references/smoke-tests.md`
2. `references/qa-evidence.md`
3. `references/acceptance-gates.md`

## Output standard

1. Report pass/fail per check.
2. Include evidence links or artifacts.
3. End with clear release recommendation.
