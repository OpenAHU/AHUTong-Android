package com.ahu.ahutong.core.common

import android.os.Build

/**
 * Application version info without depending on app [BuildConfig].
 */
object AppVersion {
    fun name(): String {
        val app = AppContextHolder.requireApp()
        return runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "0" }
    }

    fun code(): Int {
        val app = AppContextHolder.requireApp()
        return runCatching {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.getOrDefault(0)
    }
}
