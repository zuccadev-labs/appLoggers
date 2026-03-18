package com.applogger.core.model

import kotlinx.serialization.Serializable

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
