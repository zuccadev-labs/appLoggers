---
name: applogger-release-tagging
description: 'Create and verify release tags for AppLoggers after dev -> main delivery is complete. Use when main is verified and you need tag creation, release workflow checks, GitHub Packages verification, or JitPack validation.'
argument-hint: 'Provide the target version tag and whether main has already been merged and verified.'
user-invocable: true
---

# AppLoggers Release Tagging

## When to Use

Use this skill only after `dev -> main` is merged and `main` is verified, and only when the delivered change deserves a release version.

Trigger phrases include:

1. `create the release tag`
2. `publish this version`
3. `verify the release workflow`
4. `check JitPack after tagging`

## Hard Rules

1. Never tag from `dev`.
2. Never tag before `main` is current and verified.
3. Confirm the version source matches the intended tag.
4. Verify the release workflow result after pushing the tag.
5. Do not create a tag for documentation-only changes.
6. Do not create a tag for repo-only workflow, automation, or housekeeping changes unless they intentionally change release behavior or published artifacts.
7. Create a tag only for changes that alter shipped SDK artifacts, runtime behavior, public API, integration behavior, or an intentional release milestone.

## Tag Eligibility

Create a tag when at least one of these is true:

1. `sdk/` code changed in a way that affects published artifacts.
2. Public API behavior changed.
3. Runtime behavior, delivery behavior, or integration behavior changed.
4. The release is intentionally versioned as a milestone even if changes are small.

Do not create a tag when all of these are true:

1. Changes are documentation-only.
2. Changes are internal workflow-only.
3. No published artifact behavior changed.
4. No intentional release milestone was requested.

## User Intent Interpretation

Interpret requests this way:

1. `merge dev to main` means integrate reviewed work into `main`; it does not imply tagging.
2. `publish release` or `create tag` means run tag eligibility checks first.
3. For docs-only work, merging to `main` can be correct without creating any tag.

## Procedure

1. Read [release sequence](./references/release-sequence.md).
2. Read [tag decision matrix](./references/tag-decision-matrix.md).
2. Run [release verification](./references/release-verification.md).
3. If release config changed, verify package permissions and version propagation.
4. Confirm the GitHub Release and package publication completed.

## Output Standard

1. State the tag and target commit.
2. State workflow run result.
3. State whether GitHub Packages and JitPack are expected to resolve.
