package com.example.example

import android.app.Application
import com.applogger.core.AppLoggerSDK

/**
 * Application class de ejemplo.
 * En una app real, este archivo vive en tu módulo :app.
 */
class ExampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializar el SDK con la config desde BuildConfig (local.properties en dev).
        // En producción, LOGGER_URL/KEY vienen de CI secrets → BuildConfig.
        val config = ExampleApplication.buildConfig()
        val transport = ExampleApplication.buildTransport()

        AppLoggerSDK.initialize(
            context = this,
            config = config,
            transport = transport
        )
    }
}
