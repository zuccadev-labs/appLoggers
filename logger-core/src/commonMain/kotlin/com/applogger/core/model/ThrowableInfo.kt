package com.applogger.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ThrowableInfo(
    val type: String,
    val message: String?,
    val stackTrace: List<String>
)
