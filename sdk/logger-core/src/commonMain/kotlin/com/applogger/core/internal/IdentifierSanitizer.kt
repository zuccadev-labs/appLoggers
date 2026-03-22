package com.applogger.core.internal

import com.applogger.core.sha256Hex
import com.applogger.core.model.DeviceInfo

private const val UUID_HEX_LENGTH = 32
private const val UUID_STRING_LENGTH = 36
private const val UUID_VERSION_INDEX = 12
private const val UUID_VARIANT_INDEX = 16
private const val UUID_VERSION_NIBBLE = '5'
private const val UUID_FILL_CHAR = '0'

private const val SEGMENT_ONE_START = 0
private const val SEGMENT_ONE_LENGTH = 8
private const val SEGMENT_TWO_START = 8
private const val SEGMENT_TWO_LENGTH = 4
private const val SEGMENT_THREE_START = 12
private const val SEGMENT_THREE_LENGTH = 4
private const val SEGMENT_FOUR_START = 16
private const val SEGMENT_FOUR_LENGTH = 4
private const val SEGMENT_FIVE_START = 20
private const val SEGMENT_FIVE_LENGTH = 12

private val uuidRegex = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
)

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
    val base = normalized.padEnd(UUID_HEX_LENGTH, UUID_FILL_CHAR).take(UUID_HEX_LENGTH).toCharArray()

    // UUID version nibble (v5) and RFC 4122 variant bits.
    base[UUID_VERSION_INDEX] = UUID_VERSION_NIBBLE
    base[UUID_VARIANT_INDEX] = when (base[UUID_VARIANT_INDEX]) {
        '0', '1', '2', '3' -> '8'
        '4', '5', '6', '7' -> '9'
        '8', '9', 'a', 'b' -> base[UUID_VARIANT_INDEX]
        else -> 'a'
    }

    return buildString(UUID_STRING_LENGTH) {
        appendRange(base, SEGMENT_ONE_START, SEGMENT_ONE_START + SEGMENT_ONE_LENGTH)
        append('-')
        appendRange(base, SEGMENT_TWO_START, SEGMENT_TWO_START + SEGMENT_TWO_LENGTH)
        append('-')
        appendRange(base, SEGMENT_THREE_START, SEGMENT_THREE_START + SEGMENT_THREE_LENGTH)
        append('-')
        appendRange(base, SEGMENT_FOUR_START, SEGMENT_FOUR_START + SEGMENT_FOUR_LENGTH)
        append('-')
        appendRange(base, SEGMENT_FIVE_START, SEGMENT_FIVE_START + SEGMENT_FIVE_LENGTH)
    }
}
