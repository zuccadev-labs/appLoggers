---
name: applogger-release-tagging
description: 'Create and verify release tags for AppLoggers after dev -> main delivery is complete. Use when main is verified and you need tag creation, release workflow checks, GitHub Packages verification, or JitPack validation.'
argument-hint: 'Provide the target version tag and whether main has already been merged and verified.'
user-invocable: true
---

# AppLoggers Release Tagging

## When to Use

Use this skill only after `dev -> main` is merged and `main` is verified.

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

## Procedure

1. Read [release sequence](./references/release-sequence.md).
2. Run [release verification](./references/release-verification.md).
3. If release config changed, verify package permissions and version propagation.
4. Confirm the GitHub Release and package publication completed.

## Output Standard

1. State the tag and target commit.
2. State workflow run result.
3. State whether GitHub Packages and JitPack are expected to resolve.