package com.applogger.core

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.applogger.core.model.DeviceInfo

/**
 * Proveedor de metadatos del dispositivo Android.
 * NUNCA incluye PII.
 */
internal class AndroidDeviceInfoProvider(
    private val context: Context,
    private val platform: Platform
) : DeviceInfoProvider {

    override fun get(): DeviceInfo {
        val pm = context.packageManager
        val packageInfo = try {
            pm.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        return DeviceInfo(
            brand = Build.BRAND,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            platform = when (platform) {
                Platform.ANDROID_TV -> "android_tv"
                Platform.WEAR_OS -> "wear_os"
                Platform.ANDROID_MOBILE -> "android_mobile"
                Platform.JVM -> "jvm"
            },
            appVersion = packageInfo?.versionName ?: "unknown",
            appBuild = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode?.toInt() ?: 0
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode ?: 0
            },
            isLowRamDevice = activityManager?.isLowRamDevice == true,
            isTV = platform == Platform.ANDROID_TV,
            connectionType = getConnectionType()
        )
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun getConnectionType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "none"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return "none"
            val capabilities = cm.getNetworkCapabilities(network) ?: return "none"
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = cm.activeNetworkInfo ?: return "none"
            @Suppress("DEPRECATION")
            return if (networkInfo.isConnected) {
                @Suppress("DEPRECATION")
                when (networkInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> "wifi"
                    ConnectivityManager.TYPE_MOBILE -> "cellular"
                    ConnectivityManager.TYPE_ETHERNET -> "ethernet"
                    else -> "other"
                }
            } else "none"
        }
    }
}
