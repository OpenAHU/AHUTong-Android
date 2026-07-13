package com.ahu.ahutong.core.common

import android.content.Context
import android.content.Intent

/**
 * Launch intents for the host [com.ahu.ahutong.MainActivity] without a compile-time
 * dependency from library modules onto `:app`.
 */
object AppLaunchIntents {
    const val MAIN_ACTIVITY_CLASS = "com.ahu.ahutong.MainActivity"

    fun mainActivity(context: Context): Intent {
        return Intent().setClassName(context.packageName, MAIN_ACTIVITY_CLASS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
