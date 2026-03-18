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
     */
    fun buildConfig(): AppLoggerConfig {
        return AppLoggerConfig.Builder()
            .endpoint("https://tu-proyecto.supabase.co")  // Usar BuildConfig.LOGGER_URL
            .apiKey("tu_anon_key_aqui")                    // Usar BuildConfig.LOGGER_KEY
            .debugMode(true)                               // Usar BuildConfig.DEBUG
            .batchSize(20)
            .flushIntervalSeconds(30)
            .build()
    }

    fun buildTransport(): SupabaseTransport {
        return SupabaseTransport(
            endpoint = "https://tu-proyecto.supabase.co",  // Usar BuildConfig.LOGGER_URL
            apiKey = "tu_anon_key_aqui"                    // Usar BuildConfig.LOGGER_KEY
        )
    }

    // Para Android TV, el SDK detecta automáticamente la plataforma.
    // Si quieres forzar configuración de bajo recurso:
    fun buildTVConfig(): AppLoggerConfig {
        return AppLoggerConfig.Builder()
            .endpoint("https://tu-proyecto.supabase.co")
            .apiKey("tu_anon_key_aqui")
            .debugMode(false)
            .batchSize(5)
            .flushIntervalSeconds(60)
            .maxStackTraceLines(5)
            .flushOnlyWhenIdle(true)
            .build()
    }
}
