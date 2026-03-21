---
name: applogger-instrumentation-design
description: Design a high-value AppLogger event model for a Kotlin or KMP app. Use when the user asks what to log, where to log, how to tag events, and which metrics matter.
---

# AppLogger Instrumentation Design

## When to use this skill

Use this skill when the user needs:

1. Event taxonomy.
2. Tag conventions.
3. Metric model and priorities.

## Mandatory constraints

1. Prioritize business-critical and reliability-critical signals.
2. Avoid noisy low-value logging.
3. Keep naming stable and searchable.
4. All log levels (`debug`, `info`, `warn`, `error`, `critical`) accept an optional `throwable: Throwable?` parameter — recommend it for any error or anomaly event.
5. For classes that hold an `AppLogger` reference, recommend `Any.logD/I/W/E/C(logger, ...)` from `AppLoggerExtensions` to avoid repeating the tag manually.

## Workflow

1. Identify core user journeys.
2. Define events for success, warning, error, and critical paths.
3. Define tag conventions.
4. Define metrics and units.
5. Provide rollout plan with minimal initial scope.

## References bundled with this skill

1. `references/event-taxonomy.md`
2. `references/tag-conventions.md`
3. `references/metric-guidelines.md`

## Output standard

1. Deliver a concise taxonomy table.
2. Include anti-patterns to avoid.
3. End with phased rollout guidance.