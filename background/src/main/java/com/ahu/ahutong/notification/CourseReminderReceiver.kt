package com.ahu.ahutong.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ahu.ahutong.notification.model.CourseReminderPayload
import com.ahu.ahutong.background.backgroundCourseDate
import com.ahu.ahutong.background.backgroundHolidayForDate
import com.ahu.ahutong.data.schedule.scheduleHolidayNotice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LIVE_COUNTDOWN_DISMISSED) {
            CourseLiveUpdateHelper.cancelScheduledUpdate(context)
            return
        }

        val payload = CourseReminderPayload.fromIntent(intent) ?: return
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> {
                        val liveUpdateShown = CourseReminderNotifier.showReminder(context, payload)
                        if (liveUpdateShown) {
                            CourseLiveUpdateHelper.scheduleNextUpdate(context, payload)
                        }
                        CourseReminderScheduler.reschedule(context).join()
                    }

                    ACTION_UPDATE_LIVE_COUNTDOWN -> {
                        if (!CourseReminderCapability.shouldTryLiveCountdown(context, payload)) {
                            CourseReminderNotifier.cancelActiveReminder(context)
                            return@launch
                        }

                        val holidayNotice = backgroundCourseDate(payload.courseStartAtMillis)
                            ?.let { backgroundHolidayForDate(context, it) }
                            ?.let(::scheduleHolidayNotice)
                        val liveUpdateShown = CourseLiveUpdateHelper.showLiveUpdate(context, payload, holidayNotice)
                        if (liveUpdateShown) {
                            CourseLiveUpdateHelper.scheduleNextUpdate(context, payload)
                        } else {
                            CourseReminderNotifier.cancelActiveReminder(context)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val ACTION_REMIND = "com.ahu.ahutong.notification.ACTION_REMIND_COURSE"
        const val ACTION_UPDATE_LIVE_COUNTDOWN =
            "com.ahu.ahutong.notification.ACTION_UPDATE_LIVE_COUNTDOWN"
        const val ACTION_LIVE_COUNTDOWN_DISMISSED =
            "com.ahu.ahutong.notification.ACTION_LIVE_COUNTDOWN_DISMISSED"
    }
}
