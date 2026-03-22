package com.applogger.core.internal

import com.applogger.core.sha256Hex
import com.applogger.core.model.DeviceInfo

private val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

internal fun normalizeAnonymousUserId(raw: String): String {
    val candidate = raw.trim()
    if (candidate.isEmpty()) return ""
    if (uuidRegex.matches(candidate)) return candidate.lowercase()

    val hash = sha256Hex("applogger:user:$candidate")
    return hashHexToUuidV5(hash)
}

internal fun defaultDeviceId(deviceInfo: DeviceInfo): String {
    val fingerprint = buildString {
        append(deviceInfo.platform)
        append('|')
        append(deviceInfo.brand)
        append('|')
        append(deviceInfo.model)
        append('|')
        append(deviceInfo.osVersion)
        append('|')
        append(deviceInfo.apiLevel)
        append('|')
        append(deviceInfo.appVersion)
        append('|')
        append(deviceInfo.appBuild)
    }

    val hash = sha256Hex("applogger:device:${fingerprint.lowercase()}")
    return hashHexToUuidV5(hash)
}

private fun hashHexToUuidV5(hashHex: String): String {
    val normalized = hashHex.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.lowercase()
    val base = normalized.padEnd(32, '0').take(32).toCharArray()

    // UUID version nibble (v5) and RFC 4122 variant bits.
    base[12] = '5'
    base[16] = when (base[16]) {
        '0', '1', '2', '3' -> '8'
        '4', '5', '6', '7' -> '9'
        '8', '9', 'a', 'b' -> base[16]
        else -> 'a'
    }

    return buildString(36) {
        append(base, 0, 8)
        append('-')
        append(base, 8, 4)
        append('-')
        append(base, 12, 4)
        append('-')
        append(base, 16, 4)
        append('-')
        append(base, 20, 12)
    }
}
