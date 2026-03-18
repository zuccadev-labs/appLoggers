package com.applogger.core.internal

import com.applogger.core.generateUUID

internal class SessionManager {
    val sessionId: String = generateUUID()
}
