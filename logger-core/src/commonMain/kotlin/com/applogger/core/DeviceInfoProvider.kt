package com.applogger.core

import com.applogger.core.model.DeviceInfo

/**
 * Provides technical device metadata attached to every [com.applogger.core.model.LogEvent].
 *
 * **Privacy contract (GDPR / CCPA / LGPD compliant):**
 * - NEVER include PII: name, email, phone number, IMEI, Android ID.
 * - NEVER include GPS location.
 * - Only technical metadata: model, OS, app version, connection type.
 *
 * @see com.applogger.core.model.DeviceInfo for the data model.
 */
interface DeviceInfoProvider {

    /**
     * Returns an immutable snapshot of the current device information.
     * Called once during SDK initialization.
     */
    fun get(): DeviceInfo
}
