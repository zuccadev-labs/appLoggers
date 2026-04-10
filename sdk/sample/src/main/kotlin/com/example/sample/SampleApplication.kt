package com.example.sample

import com.example.applogger.sample.BuildConfig
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
 * .integritySecret(BuildConfig.LOGGER_INTEGRITY_SECRET)
 * .integritySecretId(BuildConfig.LOGGER_INTEGRITY_SECRET_ID)
 * .debugMode(BuildConfig.DEBUG)
 * ```
 * Y en build.gradle.kts:
 * ```kotlin
 * buildConfigField("String", "LOGGER_URL", "\"${localProperties["APPLOGGER_URL"]}\"")
 * buildConfigField("String", "LOGGER_KEY", "\"${localProperties["APPLOGGER_ANON_KEY"]}\"")
 * buildConfigField("String", "LOGGER_INTEGRITY_SECRET", "\"${localProperties["APPLOGGERS_INTEGRITY_SECRET"]}\"")
 * buildConfigField("String", "LOGGER_INTEGRITY_SECRET_ID", "\"${localProperties["APPLOGGERS_INTEGRITY_SECRET_ID"]}\"")
 * ```
 */
object SampleApplication {

    private val sampleUrl get() = BuildConfig.LOGGER_URL
    private val sampleKey get() = BuildConfig.LOGGER_KEY
    private val sampleDebug get() = BuildConfig.LOGGER_DEBUG
    private val integritySecret get() = BuildConfig.LOGGER_INTEGRITY_SECRET
    private val integritySecretId get() = BuildConfig.LOGGER_INTEGRITY_SECRET_ID

    fun buildConfig(): AppLoggerConfig {
        return AppLoggerConfig.Builder()
            .endpoint(sampleUrl)
            .apiKey(sampleKey)
            .integritySecret(integritySecret)
            .integritySecretId(integritySecretId)
            .debugMode(sampleDebug)
            .environment(if (sampleDebug) "development" else "production")
            .batchSize(20)
            .flushIntervalSeconds(30)
            .build()
    }

    fun buildTransport(): SupabaseTransport {
        return SupabaseTransport(
            endpoint = sampleUrl,
            apiKey = sampleKey
        )
    }

    // Para Android TV, el SDK detecta automáticamente la plataforma.
    // Si quieres forzar configuración de bajo recurso:
    fun buildTVConfig(): AppLoggerConfig {
        return AppLoggerConfig.Builder()
            .endpoint(sampleUrl)
            .apiKey(sampleKey)
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
