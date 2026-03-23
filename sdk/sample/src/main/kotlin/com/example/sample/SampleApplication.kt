package com.example.sample

import com.applogger.core.AppLoggerConfig
import com.applogger.transport.supabase.SupabaseTransport

/**
 * Ejemplo de inicialización del SDK AppLogger en Application.onCreate().
 *
 * En una app real esto estaría en tu clase Application:
 *
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         SampleApplication.initializeLogger(this)
 *     }
 * }
 * ```
 */
object SampleApplication {

    /**
     * Inicializa el SDK con Supabase como transporte.
     *
     * IMPORTANTE: En producción, usa BuildConfig para las credenciales.
     * Nunca hardcodees URLs ni API keys.
     *
     * En tu build.gradle.kts:
     * ```kotlin
     * buildConfigField("String", "LOGGER_URL", "\"${localProperties["APPLOGGER_URL"]}\"")
     * buildConfigField("String", "LOGGER_KEY", "\"${localProperties["APPLOGGER_ANON_KEY"]}\"")
     * ```
     */
    fun buildConfig(): AppLoggerConfig {
        return AppLoggerConfig.Builder()
            .endpoint(BuildConfig.LOGGER_URL)
            .apiKey(BuildConfig.LOGGER_KEY)
            .debugMode(BuildConfig.DEBUG)
            .environment(if (BuildConfig.DEBUG) "development" else "production")
            .batchSize(20)
            .flushIntervalSeconds(30)
            .build()
    }

    fun buildTransport(): SupabaseTransport {
        return SupabaseTransport(
            endpoint = BuildConfig.LOGGER_URL,
            apiKey = BuildConfig.LOGGER_KEY
        )
    }

    // Para Android TV, el SDK detecta automáticamente la plataforma.
    // Si quieres forzar configuración de bajo recurso:
    fun buildTVConfig(): AppLoggerConfig {
        return AppLoggerConfig.Builder()
            .endpoint(BuildConfig.LOGGER_URL)
            .apiKey(BuildConfig.LOGGER_KEY)
            .debugMode(false)
            .environment("production")
            .batchSize(5)
            .flushIntervalSeconds(60)
            .maxStackTraceLines(5)
            .flushOnlyWhenIdle(true)
            .build()
    }
}
