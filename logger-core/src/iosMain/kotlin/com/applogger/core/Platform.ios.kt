package com.applogger.core

import platform.Foundation.NSUUID
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.timeIntervalSince1970

actual fun generateUUID(): String = NSUUID().UUIDString

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun platformLog(tag: String, message: String) {
    NSLog("[$tag] $message")
}
