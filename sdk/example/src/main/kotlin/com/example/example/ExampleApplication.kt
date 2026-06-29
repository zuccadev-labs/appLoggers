package com.example.example

import com.example.applogger.example.BuildConfig
import com.applogger.core.AppLoggerConfig
import com.applogger.transport.supabase.SupabaseTransport

/**
 * Ejemplo de inicialización del SDK AppLogger en Application.onCreate().
 *
 * Las credenciales se resuelven con esta prioridad:
 * 1. Variable de entorno (CI / servidor)
 * 2. `local.properties` vía BuildConfig (desarrollo local)
 *
 * NUNCA comitear credenciales reales en el código fuente.
 * `local.properties` está en `.gitignore`.
 */
object ExampleApplication {

    private val integritySecret: String by lazy {
        System.getenv("APPLOGGERS_INTEGRITY_SECRET") ?: BuildConfig.LOGGER_INTEGRITY_SECRET
    }
    private val integritySecretId: String by lazy {
        System.getenv("APPLOGGERS_INTEGRITY_SECRET_ID") ?: BuildConfig.LOGGER_INTEGRITY_SECRET_ID
    }
    private val exampleUrl: String by lazy {
        System.getenv("APPLOGGER_URL") ?: BuildConfig.LOGGER_URL
    }
    private val exampleKey: String by lazy {
        System.getenv("APPLOGGER_ANON_KEY") ?: BuildConfig.LOGGER_KEY
    }
    private val exampleDebug: Boolean by lazy {
        (System.getenv("APPLOGGER_DEBUG") ?: BuildConfig.LOGGER_DEBUG.toString()).toBooleanStrictOrNull() ?: false
    }

    fun buildConfig(): AppLoggerConfig {
        return AppLoggerConfig.Builder()
            .endpoint(exampleUrl)
            .apiKey(exampleKey)
            .integritySecret(integritySecret)
            .integritySecretId(integritySecretId)
            .debugMode(exampleDebug)
            .environment(if (exampleDebug) "development" else "production")
            .batchSize(20)
            .flushIntervalSeconds(30)
            .build()
    }

    fun buildTransport(): SupabaseTransport {
        return SupabaseTransport(
            endpoint = exampleUrl,
            apiKey = exampleKey
        )
    }

    // Para Android TV, el SDK detecta automáticamente la plataforma.
    // Si quieres forzar configuración de bajo recurso:
    fun buildTVConfig(): AppLoggerConfig {
        return AppLoggerConfig.Builder()
            .endpoint(exampleUrl)
            .apiKey(exampleKey)
            .integritySecret(integritySecret)
            .integritySecretId(integritySecretId)
            .debugMode(false)
            .environment("production")
            .batchSize(5)
            .flushIntervalSeconds(60)
            .maxStackTraceLines(5)
            .flushOnlyWhenIdle(true)
            .build()
    }
}
