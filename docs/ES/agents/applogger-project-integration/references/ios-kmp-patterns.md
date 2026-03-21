# iOS KMP Integration Patterns

## Good places to initialize

1. Shared Kotlin bootstrap used by the iOS app startup.
2. A dedicated `iosMain` initializer object.
3. Shared telemetry facade exposed from Kotlin.

## Good first logging points

1. Shared module initialization completed.
2. API failure surfaced through shared code.
3. Critical user flow failure handled in Kotlin.

## Health verification

Use `AppLoggerHealth.snapshot()` from Kotlin after initialization and after one emitted event.

## Common review questions

1. Is the project truly KMP-first for iOS?
2. Where are remote endpoint and keys injected?
3. Should logging happen directly in `iosMain` or in shared abstractions?
