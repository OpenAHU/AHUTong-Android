package com.ahu.ahutong.data.mail

import android.content.Context

/** Display preference only; no account, message or credential data. */
internal object StudentMailDisplayPreferences {
    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences("student-mail-display", Context.MODE_PRIVATE)

    fun autoLoadExternalImages(context: Context): Boolean = preferences(context)
        .getBoolean("auto-load-external-images", true)

    fun setAutoLoadExternalImages(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("auto-load-external-images", enabled).apply()
    }
}
