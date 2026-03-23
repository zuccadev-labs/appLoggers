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
 *
 * NOTA: En producción, reemplaza las constantes de abajo con BuildConfig:
 * ```kotlin
 * .endpoint(BuildConfig.LOGGER_URL)
 * .apiKey(BuildConfig.LOGGER_KEY)
 * .debugMode(BuildConfig.DEBUG)
 * ```
 * Y en build.gradle.kts:
 * ```kotlin
 * buildConfigField("String", "LOGGER_URL", "\"${localProperties["APPLOGGER_URL"]}\"")
 * buildConfigField("String", "LOGGER_KEY", "\"${localProperties["APPLOGGER_ANON_KEY"]}\"")
 * ```
 */
object SampleApplication {

    // Reemplazar con BuildConfig.LOGGER_URL en una app real
    private const val SAMPLE_URL = ""
    // Reemplazar con BuildConfig.LOGGER_KEY en una app real
    private const val SAMPLE_KEY = ""
    // Reemplazar con BuildConfig.DEBUG en una app real
    private const val SAMPLE_DEBUG = false

    fun buildConfig(): AppLoggerConfig {
        return AppLoggerConfig.Builder()
            .endpoint(SAMPLE_URL)
            .apiKey(SAMPLE_KEY)
            .debugMode(SAMPLE_DEBUG)
            .environment(if (SAMPLE_DEBUG) "development" else "production")
            .batchSize(20)
            .flushIntervalSeconds(30)
            .build()
    }

    fun buildTransport(): SupabaseTransport {
        return SupabaseTransport(
            endpoint = SAMPLE_URL,
            apiKey = SAMPLE_KEY
        )
    }

    // Para Android TV, el SDK detecta automáticamente la plataforma.
    // Si quieres forzar configuración de bajo recurso:
    fun buildTVConfig(): AppLoggerConfig {
        return AppLoggerConfig.Builder()
            .endpoint(SAMPLE_URL)
            .apiKey(SAMPLE_KEY)
            .debugMode(false)
            .environment("production")
            .batchSize(5)
            .flushIntervalSeconds(60)
            .maxStackTraceLines(5)
            .flushOnlyWhenIdle(true)
            .build()
    }
}
