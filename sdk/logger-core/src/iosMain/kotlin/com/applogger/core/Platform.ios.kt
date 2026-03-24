package com.applogger.core

import platform.Foundation.NSUUID
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.timeIntervalSince1970
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CommonCrypto.CC_SHA256
import platform.CommonCrypto.CC_SHA256_DIGEST_LENGTH

private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private const val HEX_BYTE_WIDTH = 2

actual fun generateUUID(): String = NSUUID().UUIDString

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun platformLog(tag: String, message: String) {
    NSLog("[$tag] $message")
}

actual fun hmacSha256Hex(secret: String, data: String): String = "" // iOS stub — not required for JVM tests

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

    return digest.joinToString(separator = "") { byte ->
        byte.toInt().and(BYTE_MASK).toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
    }
}
