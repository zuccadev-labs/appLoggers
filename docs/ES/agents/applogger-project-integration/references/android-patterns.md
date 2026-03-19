# Android Integration Patterns

## Good places to initialize

1. `Application`
2. DI bootstrap (`Hilt`, `Koin`, manual service locator)
3. Startup component that already wires analytics or crash reporting

## Good first logging points

1. App startup completed
2. Authentication failure
3. Network anomaly or timeout
4. Critical purchase or playback failure

## Health verification

Use `AppLoggerHealth.snapshot()` after initialization and after one emitted event.

## Common review questions

1. Is there already a wrapper around logs?
2. Should AppLogger be called directly or behind an app-specific facade?
3. Where are secrets loaded today?