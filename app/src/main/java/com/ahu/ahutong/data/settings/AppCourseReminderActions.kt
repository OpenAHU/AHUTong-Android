package com.ahu.ahutong.data.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ahu.ahutong.notification.CourseReminderCapability
import com.ahu.ahutong.notification.CourseReminderNotifier
import com.ahu.ahutong.notification.CourseReminderScheduler
import com.ahu.ahutong.ui.settings.CourseReminderActions
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCourseReminderActions @Inject constructor() : CourseReminderActions {
    override fun reschedule(context: Context) {
        CourseReminderScheduler.reschedule(context)
    }

    override fun cancel(context: Context) {
        CourseReminderScheduler.cancel(context)
    }

    override fun cancelActiveReminder(context: Context) {
        CourseReminderNotifier.cancelActiveReminder(context)
    }

    override fun canUseLiveCountdown(context: Context): Boolean =
        CourseReminderCapability.isAndroid16Plus()

    override fun createPromotionSettingsIntent(context: Context): Intent? =
        CourseReminderCapability.createPromotionSettingsIntent(context)

    override fun createNotificationSettingsIntent(context: Context): Intent? =
        CourseReminderCapability.createNotificationSettingsIntent(context)

    override fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
