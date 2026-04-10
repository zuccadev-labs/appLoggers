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
6. Recommend `withTag()` for classes that always log under the same domain tag — returns a `TaggedLogger` with fixed tag.
7. Recommend `timed{}` for measuring latency of any operation.
8. Recommend `logCatching{}` to replace try/catch boilerplate around operations that should log on failure.
9. Recommend `loggerTag<T>()` for companion objects to avoid string literal tags.
13. Recommend `newScope(vararg attributes)` when multiple events in a flow share the same context — avoids passing the same extra on every call. Use `childScope()` for nested contexts (session → player → segment).
14. Do NOT recommend `addGlobalExtra()` for per-operation context — it pollutes the entire SDK. Use `newScope()` for isolated, per-operation context that doesn't affect other concurrent callers.
10. `extra` values accept `Map<String, Any>` — Int, Long, Double, Boolean are preserved as native JSON primitives in Supabase JSONB, enabling typed queries.
11. For operations that need duration measurement, recommend `OperationTrace` via `AppLoggerSDK.startTrace(name, vararg attributes)` — it emits `trace.<name>` metric with `duration_ms` automatically on `end()` or an ERROR event on `endWithError(error)`.
12. For bandwidth-sensitive production apps, recommend `dailyDataLimitMb(n)` in `AppLoggerConfig.Builder()` — the SDK sheds non-critical events when the daily byte limit is reached; ERROR and CRITICAL are never shed.
13. Recommend a fixed domain tag registry such as `BOOT`, `AUTH`, `API`, `GRPC`, `FILTER`, `SYSTEM`, `CRASH` instead of ad-hoc per-class strings.
14. Do not put PII or stable identifiers like JWTs, emails, or raw user IDs in message text.
15. Prefer stable, grep-friendly production messages over decorative emojis or highly variable prose.
16. For anomalies, prefer structured dimensions such as `anomalyType`, `reason`, `status`, or `platform` in addition to the message.
17. For long-lived services, include lifecycle metrics like startup latency or uptime.

## Workflow

1. Identify core user journeys.
2. Define events for success, warning, error, and critical paths.
3. Define tag conventions.
4. Define metrics and units.
5. Provide rollout plan with minimal initial scope.

## Real-world design pattern to emulate

1. Assign tags by functional domain, not by file name.
2. Pair important warnings/errors with a count or latency metric when operators will need trend visibility.
3. Use BOOT logs for startup, AUTH for token validation, API/GRPC for server surfaces, FILTER or PLAYER for domain engines, and CRASH for uncaught exceptions.
4. Reserve CRITICAL logs for true fatal or service-threatening conditions.
5. Model dimensions like `reason`, `status`, `platform`, `action`, and `anomalyType` so downstream filters do not depend on parsing prose.

## References bundled with this skill

1. `references/event-taxonomy.md`
2. `references/tag-conventions.md`
3. `references/metric-guidelines.md`

## Output standard

1. Deliver a concise taxonomy table.
2. Include anti-patterns to avoid.
3. End with phased rollout guidance.
