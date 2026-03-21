---
name: applogger-repo-context
description: 'Understand the AppLoggers repository structure, branch policy, CI/release workflows, key paths, and validation commands. Use when navigating modules, routes, architecture, or delivery rules before making changes.'
argument-hint: 'Describe what repo context you need: structure, commands, branches, CI, release, docs, or modules.'
user-invocable: true
---

# AppLoggers Repo Context

## When to Use

Use this skill when you need to understand the repository before changing or delivering anything:

1. Repo structure and module layout.
2. Branch and merge policy.
3. CI and release triggers.
4. Where docs and package files live.

## Procedure

1. Read [repo map](./references/repo-map.md).
2. Read [delivery matrix](./references/delivery-matrix.md).
3. Read [command baseline](./references/command-baseline.md).
4. Identify impacted modules, docs, and workflows before editing.

## Constraints

1. Do not assume paths or branch policy from memory.
2. Treat `main` as stable and `dev` as the integration branch.
3. Confirm workflow triggers before suggesting push, PR, or tag operations.

## Output Standard

1. Name the affected modules and docs.
2. State the branch/release implications.
3. Give exact commands when they matter.
