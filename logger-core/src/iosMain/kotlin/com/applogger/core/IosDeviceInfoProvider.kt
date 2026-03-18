package com.applogger.core

import com.applogger.core.model.DeviceInfo
import platform.UIKit.UIDevice
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

/**
 * Proveedor de metadatos del dispositivo iOS.
 * NUNCA incluye PII.
 */
internal class IosDeviceInfoProvider : DeviceInfoProvider {

    override fun get(): DeviceInfo {
        val infoDictionary = NSBundle.mainBundle.infoDictionary
        val appVersion = infoDictionary?.get("CFBundleShortVersionString") as? String ?: "unknown"
        val appBuild = (infoDictionary?.get("CFBundleVersion") as? String)?.toIntOrNull() ?: 0

        return DeviceInfo(
            brand = "Apple",
            model = UIDevice.currentDevice.model,
            osVersion = UIDevice.currentDevice.systemVersion,
            apiLevel = 0, // No aplica en iOS
            platform = "ios",
            appVersion = appVersion,
            appBuild = appBuild,
            isLowRamDevice = NSProcessInfo.processInfo.physicalMemory < 2_000_000_000uL,
            isTV = false,
            connectionType = "unknown" // Requiere Reachability framework en producción
        )
    }
}
