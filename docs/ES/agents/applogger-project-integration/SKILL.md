---
name: applogger-project-integration
description: Inspect an existing Kotlin or Kotlin Multiplatform app, identify the best integration points for AppLogger, and wire the SDK into the project safely. Use when the user says things like "lee la app e integra el sdk", "analiza el proyecto y agrega AppLogger", or "dime dónde inicializar y usar el logger".
---

# AppLogger Project Integration

## When to use this skill

Use this skill when the user wants the agent to:

1. Read the existing app structure.
2. Decide where AppLogger should be initialized.
3. Integrate the SDK with minimal disruption.
4. Add representative logging calls in the right places.

Do not use this skill when:

1. The user only wants a step-by-step installation guide.
2. The target project is not Kotlin or KMP based.
3. The request is about backend schema work instead of client integration.

## Mandatory constraints

1. Inspect the project before proposing code changes.
2. Reuse the app's existing architecture patterns.
3. Avoid broad invasive changes on the first pass.
4. Keep iOS guidance KMP-only.

## Workflow

1. Identify modules, entry points, and platform targets.
2. Detect current logging, crash reporting, and configuration patterns.
3. Choose the correct initialization point.
4. Add AppLogger dependencies and bootstrap code.
5. Add a small set of high-value logging points.
6. Add a health check or smoke validation path.
7. Run build/tests when available.

## References bundled with this skill

1. `references/integration-playbook.md`
2. `references/android-patterns.md`
3. `references/ios-kmp-patterns.md`

## Output standard

1. Explain why each integration point was chosen.
2. Distinguish required changes from optional improvements.
3. Call out assumptions and unknowns.
4. End with a short validation plan.