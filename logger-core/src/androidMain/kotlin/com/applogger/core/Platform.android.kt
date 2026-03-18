package com.applogger.core

import android.util.Log
import java.util.UUID

actual fun generateUUID(): String = UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun platformLog(tag: String, message: String) {
    Log.d(tag, message)
}
