package com.applogger.core

import platform.Foundation.NSUUID
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.timeIntervalSince1970
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CommonCrypto.CC_SHA256
import platform.CommonCrypto.CC_SHA256_DIGEST_LENGTH

actual fun generateUUID(): String = NSUUID().UUIDString

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun platformLog(tag: String, message: String) {
    NSLog("[$tag] $message")
}

actual fun sha256Hex(input: String): String {
    val data = input.encodeToByteArray()
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)

    data.usePinned { dataPinned ->
        digest.usePinned { digestPinned ->
            CC_SHA256(
                dataPinned.addressOf(0),
                data.size.toUInt(),
                digestPinned.addressOf(0)
            )
        }
    }

    return digest.joinToString(separator = "") { byte -> byte.toInt().and(0xFF).toString(16).padStart(2, '0') }
}
