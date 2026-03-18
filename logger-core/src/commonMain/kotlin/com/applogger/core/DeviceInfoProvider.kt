package com.applogger.core

import com.applogger.core.model.DeviceInfo

/**
 * Provee metadatos técnicos del dispositivo.
 *
 * Contrato de privacidad:
 * - NUNCA incluir PII: nombre, email, número de teléfono, IMEI, Android ID.
 * - NUNCA incluir ubicación GPS.
 * - Solo metadatos técnicos: modelo, SO, versión de app, tipo de conexión.
 */
interface DeviceInfoProvider {
    fun get(): DeviceInfo
}
