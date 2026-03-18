package com.applogger.core

import com.applogger.core.model.DeviceInfo

/**
 * Proveedor de metadatos del dispositivo JVM.
 */
internal class JvmDeviceInfoProvider : DeviceInfoProvider {

    override fun get(): DeviceInfo {
        return DeviceInfo(
            brand = System.getProperty("os.name") ?: "JVM",
            model = System.getProperty("os.arch") ?: "unknown",
            osVersion = System.getProperty("os.version") ?: "unknown",
            apiLevel = 0,
            platform = "jvm",
            appVersion = "0.0.0",
            appBuild = 0,
            isLowRamDevice = Runtime.getRuntime().maxMemory() < 256 * 1024 * 1024,
            isTV = false,
            connectionType = "ethernet"
        )
    }
}
