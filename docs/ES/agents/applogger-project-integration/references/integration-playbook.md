# Integration Playbook

## What to inspect first

1. Root and module Gradle files.
2. Android `Application` class or DI bootstrap.
3. Shared KMP module entry points.
4. Existing logging, analytics, or crash SDKs.
5. Local configuration sources such as `local.properties`, env files, or `BuildConfig`.

## Preferred initialization points

1. Android: `Application.onCreate()` or the main DI/bootstrap layer.
2. KMP iOS: shared Kotlin bootstrap invoked from app startup code.
3. Shared business logic: helper wrappers in `commonMain` for repeated usage.

## First-pass integration policy

1. Add dependencies.
2. Initialize once.
3. Add one startup log.
4. Add one network or error log.
5. Add one health-check path.

## What not to do on first pass

1. Do not replace every existing logger call in the whole codebase.
2. Do not add PII to logs.
3. Do not add AppLogger to unrelated layers without a reason.
4. Do not introduce platform-specific iOS host code if the project is KMP.