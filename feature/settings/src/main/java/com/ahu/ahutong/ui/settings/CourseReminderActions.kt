package com.ahu.ahutong.ui.settings

import android.content.Context
import android.content.Intent

/**
 * App-bound side effects for course reminder preferences.
 */
interface CourseReminderActions {
    fun reschedule(context: Context)

    fun cancel(context: Context)

    fun cancelActiveReminder(context: Context)

    fun canUseLiveCountdown(context: Context): Boolean

    fun createPromotionSettingsIntent(context: Context): Intent?

    fun createNotificationSettingsIntent(context: Context): Intent?

    fun hasNotificationPermission(context: Context): Boolean
}
