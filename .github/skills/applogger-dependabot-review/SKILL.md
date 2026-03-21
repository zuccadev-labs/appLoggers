---
name: applogger-dependabot-review
description: 'Review open Dependabot pull requests in AppLoggers like a senior corporate engineer. Use when deciding whether dependency or GitHub Actions updates should be merged, deferred, or rejected.'
argument-hint: 'Describe the Dependabot PR number or the update category: Gradle, Kotlin, GitHub Actions, testing, or other.'
user-invocable: true
---

# AppLoggers Dependabot Review

## When to Use

Use this skill when a Dependabot PR is open and needs expert review.

Trigger phrases include:

1. `review dependabot`
2. `should we merge this dependency update`
3. `audit this automated PR`

## Hard Rules

1. Do not merge purely because checks pass.
2. Inspect diff scope and workflow impact.
3. Consider branch target, release implications, and regression risk.
4. Prefer merging low-risk CI and tooling updates when evidence is clean.

## Procedure

1. Read [review criteria](./references/review-criteria.md).
2. Read [merge policy](./references/merge-policy.md).
3. Inspect checks, files changed, and upgrade category.
4. Decide: merge, defer, or close.

## Output Standard

1. Findings first, ordered by severity.
2. Clear merge recommendation.
3. Any follow-up validation needed.
