package com.applogger.core.model

import kotlinx.serialization.Serializable

/**
 * Technical device metadata attached to every [LogEvent].
 *
 * Contains **no PII** — only technical characteristics required for diagnostics.
 *
 * @property brand          Device manufacturer (e.g. "Samsung", "Apple").
 * @property model          Device model name (e.g. "SM-G991B", "iPhone14,2").
 * @property osVersion      OS version string (e.g. "14", "17.2").
 * @property apiLevel       Android API level or iOS major version.
 * @property platform       Platform identifier: "android", "ios", or "jvm".
 * @property appVersion     Host app version name (e.g. "2.1.0").
 * @property appBuild       Host app build/version code.
 * @property isLowRamDevice True on low-memory devices (Android `isLowRamDevice()`).
 * @property isTV           True on Android TV / tvOS devices.
 * @property connectionType Current network type: "wifi", "cellular", "ethernet", "unknown".
 */
@Serializable
data class DeviceInfo(
    val brand: String,
    val model: String,
    val osVersion: String,
    val apiLevel: Int,
    val platform: String,
    val appVersion: String,
    val appBuild: Int,
    val isLowRamDevice: Boolean,
    val isTV: Boolean = false,
    val connectionType: String
)
