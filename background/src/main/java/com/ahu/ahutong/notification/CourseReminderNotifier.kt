package com.ahu.ahutong.notification

import android.Manifest
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.ahu.ahutong.background.launchIntent
import com.ahu.ahutong.background.backgroundCourseDate
import com.ahu.ahutong.background.backgroundHolidayForDate
import com.ahu.ahutong.data.schedule.scheduleHolidayNotice
import com.ahu.ahutong.notification.model.CourseReminderPayload

object CourseReminderNotifier {
    private const val NOTIFICATION_TAG_PREFIX = "course_reminder:"
    enum class DeliveryResult { BLOCKED, STANDARD, LIVE }
    suspend fun showReminder(
        context: Context,
        payload: CourseReminderPayload
    ): DeliveryResult {
        if (!canPostNotifications(context)) return DeliveryResult.BLOCKED
        val startAt = payload.courseStartAtMillis
        if (startAt != null && startAt <= System.currentTimeMillis()) return DeliveryResult.BLOCKED

        val holidayNotice = backgroundCourseDate(payload.courseStartAtMillis)
            ?.let { backgroundHolidayForDate(context, it) }
            ?.let(::scheduleHolidayNotice)
        // 日历查询期间课程可能已开始，继续遵守提醒的过期检查。
        if (startAt != null && startAt <= System.currentTimeMillis()) return DeliveryResult.BLOCKED

        if (CourseReminderCapability.shouldTryLiveCountdown(context, payload)) {
            val shown = CourseLiveUpdateHelper.showLiveUpdate(context, payload, holidayNotice)
            if (shown) return DeliveryResult.LIVE
        }

        CourseLiveUpdateHelper.cancel(context)
        CourseLiveUpdateHelper.cancelScheduledUpdate(context)
        return if (showStandardReminder(context, payload, holidayNotice)) DeliveryResult.STANDARD else DeliveryResult.BLOCKED
    }

    fun cancelActiveReminder(context: Context) {
        CourseLiveUpdateHelper.cancel(context)
        CourseLiveUpdateHelper.cancelScheduledUpdate(context)
    }

    fun cancelStandardReminders(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications.filter { it.tag?.startsWith(NOTIFICATION_TAG_PREFIX) == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun showStandardReminder(
        context: Context,
        payload: CourseReminderPayload,
        holidayNotice: String?
    ): Boolean {
        CourseReminderScheduler.createNotificationChannel(context)

        val contentText = buildString {
            val remainingMillis = payload.courseStartAtMillis?.minus(System.currentTimeMillis())
            if (remainingMillis != null) {
                val minutes = ((remainingMillis - 1).coerceAtLeast(0) / 60_000L + 1)
                append("$minutes 分钟后上课")
            } else {
                append("课前提醒")
            }
            if (!payload.location.isNullOrBlank()) {
                append(" · ")
                append(payload.location)
            }
            if (!payload.timeText.isNullOrBlank()) {
                append(" · ")
                append(payload.timeText)
            }
        }

        val displayText = withCourseHolidayNotice(contentText, holidayNotice)
        val notification = NotificationCompat.Builder(context, CourseReminderScheduler.CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(payload.courseName)
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(context, payload.notificationId))
            .build()

        if (!canPostNotifications(context)) return false
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_TAG_PREFIX + (payload.occurrenceKey ?: "debug"), payload.notificationId, notification
            )
        } catch (_: SecurityException) {
            // Permission can be revoked between the explicit check and the notify call.
            return false
        }
        return true
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CourseReminderScheduler.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun buildContentIntent(
        context: Context,
        requestCode: Int
    ): PendingIntent? {
        return TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(launchIntent(context))
            .getPendingIntent(
                requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
