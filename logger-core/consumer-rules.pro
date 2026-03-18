# ──────────────────────────────────────────────────────────
# AppLogger SDK — ProGuard / R8 consumer rules
# Estas reglas se aplican automáticamente a apps que usen el SDK
# ──────────────────────────────────────────────────────────

# Public API — interfaces y clases públicas del SDK
-keep class com.applogger.core.AppLogger { *; }
-keep class com.applogger.core.AppLoggerConfig { *; }
-keep class com.applogger.core.AppLoggerConfig$Builder { *; }
-keep class com.applogger.core.AppLoggerVersion { *; }
-keep class com.applogger.core.LogTransport { *; }
-keep class com.applogger.core.TransportResult { *; }
-keep class com.applogger.core.TransportResult$Success { *; }
-keep class com.applogger.core.TransportResult$Failure { *; }
-keep class com.applogger.core.LogFormatter { *; }
-keep class com.applogger.core.LogFilter { *; }
-keep class com.applogger.core.ChainedLogFilter { *; }
-keep class com.applogger.core.LogBuffer { *; }
-keep class com.applogger.core.CrashHandler { *; }
-keep class com.applogger.core.DeviceInfoProvider { *; }

# Android entry point
-keep class com.applogger.core.AppLoggerSDK { *; }

# Model classes (usadas con Kotlinx Serialization)
-keep class com.applogger.core.model.LogEvent { *; }
-keep class com.applogger.core.model.LogLevel { *; }
-keep class com.applogger.core.model.DeviceInfo { *; }
-keep class com.applogger.core.model.ThrowableInfo { *; }

# Kotlinx Serialization — preservar serializers generados
-keepclassmembers class com.applogger.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.applogger.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.applogger.core.model.**$$serializer { *; }

# Enums
-keepclassmembers enum com.applogger.core.model.** { *; }

# Ktor (mantener clases que usan reflection para engines)
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
