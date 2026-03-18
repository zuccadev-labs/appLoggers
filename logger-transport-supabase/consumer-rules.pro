# ──────────────────────────────────────────────────────────
# AppLogger Supabase Transport — ProGuard / R8 consumer rules
# ──────────────────────────────────────────────────────────

# Public API
-keep class com.applogger.transport.supabase.SupabaseTransport { *; }

# Internal serializable models (usadas por Ktor/Serialization)
-keep class com.applogger.transport.supabase.SupabaseLogEntry { *; }
-keep class com.applogger.transport.supabase.SupabaseMetricEntry { *; }

# Kotlinx Serialization
-keepclassmembers class com.applogger.transport.supabase.** {
    *** Companion;
}
-keepclasseswithmembers class com.applogger.transport.supabase.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.applogger.transport.supabase.**$$serializer { *; }
