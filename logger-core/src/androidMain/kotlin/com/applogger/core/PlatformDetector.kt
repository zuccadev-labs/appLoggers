package com.applogger.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Detecta automáticamente la plataforma Android (Mobile, TV, Wear).
 */
enum class Platform(val isLowResource: Boolean) {
    ANDROID_MOBILE(isLowResource = false),
    ANDROID_TV(isLowResource = true),
    WEAR_OS(isLowResource = true),
    JVM(isLowResource = false)
}

internal object PlatformDetector {
    fun detect(context: Context): Platform {
        val pm = context.packageManager
        return when {
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) -> Platform.ANDROID_TV
            pm.hasSystemFeature(PackageManager.FEATURE_WATCH) -> Platform.WEAR_OS
            else -> Platform.ANDROID_MOBILE
        }
    }
}
