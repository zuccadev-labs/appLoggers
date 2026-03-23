package com.applogger.core

import com.applogger.core.model.DeviceInfo
import platform.UIKit.UIDevice
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_get_status
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wired
import platform.darwin.dispatch_get_main_queue

/** 100ms en nanosegundos — timeout máximo para el monitor de red en init del SDK */
private const val NETWORK_MONITOR_TIMEOUT_NS = 100_000_000L

/**
 * Proveedor de metadatos del dispositivo iOS.
 * NUNCA incluye PII.
 *
 * Usa Network.framework para detectar el tipo de conexión activa.
 * El monitor se inicia en el momento de la llamada y se cancela inmediatamente
 * después de obtener el estado inicial — no mantiene recursos en background.
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
            apiLevel = UIDevice.currentDevice.systemVersion
                .split(".")
                .firstOrNull()
                ?.toIntOrNull() ?: 0,
            platform = "ios",
            appVersion = appVersion,
            appBuild = appBuild,
            isLowRamDevice = NSProcessInfo.processInfo.physicalMemory < 2_000_000_000uL,
            isTV = false,
            connectionType = detectConnectionType()
        )
    }

    /**
     * Detecta el tipo de conexión usando Network.framework (iOS 12+).
     * Obtiene el path actual de forma síncrona usando un semáforo.
     * Retorna "wifi", "cellular", "ethernet" o "unknown".
     */
    @Suppress("TooGenericExceptionCaught")
    private fun detectConnectionType(): String {
        return try {
            val monitor = nw_path_monitor_create() ?: return "unknown"
            var connectionType = "unknown"
            val semaphore = platform.darwin.dispatch_semaphore_create(0)

            nw_path_monitor_set_update_handler(monitor) { path ->
                if (path != null && nw_path_get_status(path) == nw_path_status_satisfied) {
                    connectionType = when {
                        nw_path_uses_interface_type(path, nw_interface_type_wifi) -> "wifi"
                        nw_path_uses_interface_type(path, nw_interface_type_cellular) -> "cellular"
                        nw_path_uses_interface_type(path, nw_interface_type_wired) -> "ethernet"
                        else -> "other"
                    }
                } else {
                    connectionType = "none"
                }
                platform.darwin.dispatch_semaphore_signal(semaphore)
            }

            nw_path_monitor_start(monitor, dispatch_get_main_queue())
            // Espera máximo 100ms para no bloquear el init del SDK
            platform.darwin.dispatch_semaphore_wait(
                semaphore,
                platform.darwin.dispatch_time(
                    platform.darwin.DISPATCH_TIME_NOW,
                    NETWORK_MONITOR_TIMEOUT_NS
                )
            )
            nw_path_monitor_cancel(monitor)
            connectionType
        } catch (_: Exception) {
            "unknown"
        }
    }
}
