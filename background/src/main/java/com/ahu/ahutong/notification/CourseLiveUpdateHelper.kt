package com.ahu.ahutong.notification

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.ahu.ahutong.background.launchIntent
import com.ahu.ahutong.notification.model.CourseReminderPayload
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CourseLiveUpdateHelper {
    private const val LIVE_NOTIFICATION_ID = 4096
    private const val LIVE_UPDATE_REQUEST_CODE = 4097
    private const val LIVE_DISMISS_REQUEST_CODE = 4098
    private const val EXTRA_OCCURRENCE_KEY = "course_reminder_occurrence"

    fun showLiveUpdate(
        context: Context,
        payload: CourseReminderPayload,
        holidayNotice: String? = null
    ): Boolean {
        if (!CourseReminderCapability.isAndroid16Plus()) return false

        val courseStartAtMillis = payload.courseStartAtMillis ?: return false
        val remainingDurationMs = courseStartAtMillis - System.currentTimeMillis()
        if (remainingDurationMs <= 0) {
            cancel(context)
            cancelScheduledUpdate(context)
            return false
        }

        val startText = Instant.ofEpochMilli(courseStartAtMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm")) + " 开始上课"
        val contentText = withCourseHolidayNotice(
            listOfNotNull(payload.location?.takeIf { it.isNotBlank() }, startText).joinToString(" · "),
            holidayNotice
        )
        CourseReminderScheduler.createNotificationChannel(context)
        val notification = NotificationCompat.Builder(context, CourseReminderScheduler.CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(payload.courseName)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setWhen(courseStartAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(remainingDurationMs)
            .setRequestPromotedOngoing(true)
            .addExtras(Bundle().apply { putString(EXTRA_OCCURRENCE_KEY, payload.occurrenceKey) })
            .setContentIntent(buildContentIntent(context, payload.notificationId))
            .setDeleteIntent(buildDismissPendingIntent(context))
            .build()

        if (!hasPromotableCharacteristics(notification)) return false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            NotificationManagerCompat.from(context).notify(LIVE_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(LIVE_NOTIFICATION_ID)
    }

    fun retainCurrentOccurrence(context: Context, validKeys: Set<String>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val key = manager.activeNotifications.firstOrNull { it.id == LIVE_NOTIFICATION_ID && it.tag == null }
            ?.notification?.extras?.getString(EXTRA_OCCURRENCE_KEY) ?: return
        if (key !in validKeys) cancel(context)
    }

    fun cancelScheduledUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            LIVE_UPDATE_REQUEST_CODE,
            Intent(context, CourseReminderReceiver::class.java).apply {
                action = CourseReminderReceiver.ACTION_UPDATE_LIVE_COUNTDOWN
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun buildContentIntent(
        context: Context,
        requestCode: Int
    ): PendingIntent? {
        val openIntent = launchIntent(context)
        return TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(openIntent)
            .getPendingIntent(
                requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }

    private fun buildDismissPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            action = CourseReminderReceiver.ACTION_LIVE_COUNTDOWN_DISMISSED
        }
        return PendingIntent.getBroadcast(
            context,
            LIVE_DISMISS_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasPromotableCharacteristics(notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return notification.hasPromotableCharacteristics()
    }

}
