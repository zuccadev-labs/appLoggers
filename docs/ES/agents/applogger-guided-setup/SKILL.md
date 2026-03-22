---
name: applogger-guided-setup
description: Guide a developer step by step to install and configure the AppLogger SDK in an Android or Kotlin Multiplatform iOS project. Use when the user says things like "guiame para configurar el sdk", "ayudame a instalar AppLogger", or "configure the logger in my app".
---

# AppLogger Guided Setup

## When to use this skill

Use this skill when the user wants guided help to:

1. Add the repository and dependencies.
2. Configure environment values safely.
3. Initialize AppLogger correctly.
4. Validate basic health checks and first events.

Do not use this skill when:

1. The user wants the agent to inspect the whole app and decide integration points automatically.
2. The target project is not Kotlin or Kotlin Multiplatform.
3. The user asks for native iOS host integration outside KMP.

## Mandatory constraints

1. Prefer Kotlin-first examples.
2. Treat iOS as KMP-only in this SDK.
3. Never recommend hardcoding secrets in committed files.
4. Keep the first integration minimal before adding advanced tuning.
5. When handling configuration, verify `local.properties` keys first.
6. If AppLogger keys are missing, add only missing keys directly.
7. Never modify, rename, or delete unrelated existing variables in `local.properties`.
8. When `APPLOGGER_DEBUG=true`, AppLogger outputs automatically to Logcat — do not instruct the user to add any additional Logcat or logging configuration.
9. Never set `APPLOGGER_DEBUG=true` in production builds.
10. Use canonical imports from this SDK only: `com.applogger.core.*` and `com.applogger.transport.supabase.SupabaseTransport`.
11. Never suggest `com.applogger.sdk.*` imports; that package is not valid for this SDK version.
12. State Logcat behavior precisely: output is visible only when `isDebugMode=true` and `consoleOutput=true`.
13. Treat `BuildConfig.LOGGER_URL`, `BuildConfig.LOGGER_KEY`, and `BuildConfig.LOGGER_DEBUG` as placeholders unless the target app already defines them.
14. If Android snippets use `BuildConfig.*` and fields are missing, provide the Gradle mapping step before initialization code.
15. Never use `AppLoggerSDK` in iOS/KMP setup code; use `AppLoggerIos.shared` for iOS KMP initialization.

## Workflow

1. Identify whether the target is Android, iOS KMP, or both.
2. Ask for or inspect the relevant Gradle files.
3. Add JitPack and the required AppLogger dependencies.
4. Inspect `local.properties` and verify required AppLogger keys.
5. Add only missing AppLogger keys to `local.properties` without changing other variables.
6. Configure runtime flags.
7. Add initialization code in the correct app entry point.
8. Add one or two logging calls and a health check.
9. Explain how to verify the integration.

## References bundled with this skill

1. `references/android-setup.md`
2. `references/ios-kmp-setup.md`

## Output standard

1. Start with required changes only.
2. Separate Android and iOS instructions clearly.
3. Explain where each snippet goes.
4. Include a short validation checklist at the end.
