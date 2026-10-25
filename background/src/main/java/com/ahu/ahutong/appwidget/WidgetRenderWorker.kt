package com.ahu.ahutong.appwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ahu.ahutong.background.backgroundCourseDate
import com.ahu.ahutong.background.backgroundHolidayForDate
import com.ahu.ahutong.data.debug.DebugClock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Rendering can wait on Keystore or the widget host; it must not hold a broadcast open. */
class WidgetRenderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val rendered = withTimeout(20_000L) {
                if (inputData.getBoolean(REFRESH_HOLIDAY, false)) {
                    backgroundCourseDate(DebugClock.nowDate().time)?.let {
                        backgroundHolidayForDate(applicationContext, it, refresh = true)
                    }
                    // Re-read the latest timetable only in the normal rendering lane.
                    enqueue(applicationContext)
                    return@withTimeout true
                }
                renderWidgetStyles(
                    glance = { ScheduleAppWidget().updateAll(applicationContext) },
                    adaptive = {
                        val manager = AppWidgetManager.getInstance(applicationContext)
                        val ids = manager.getAppWidgetIds(
                            ComponentName(applicationContext, ScheduleAdaptiveWidgetProvider::class.java)
                        )
                        ScheduleAdaptiveWidgetProvider().renderCachedWidgets(applicationContext, manager, ids)
                    },
                    onFailure = { style, error -> Log.w(TAG, "Unable to render $style widgets", error) }
                )
            }
            if (rendered) Result.success() else if (runAttemptCount == 0) Result.retry() else Result.failure()
        } catch (error: TimeoutCancellationException) {
            Log.w(TAG, "Cached widget render exceeded its budget")
            Result.failure()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Unable to render cached widgets", error)
            if (runAttemptCount == 0) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "WidgetRenderWorker"
        private const val WORK_NAME = "widget-cached-render"
        private const val HOLIDAY_WORK_NAME = "widget-holiday-render"
        private const val REFRESH_HOLIDAY = "refresh-holiday"

        internal fun enqueue(context: Context, refreshHoliday: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<WidgetRenderWorker>()
                .setInputData(workDataOf(REFRESH_HOLIDAY to refreshHoliday))
            // Older Android versions require a foreground notification for expedited work.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                // Holiday publication requests a cached redraw. That redraw must not
                // cancel the user's explicit calendar refresh halfway through.
                if (refreshHoliday) HOLIDAY_WORK_NAME else WORK_NAME,
                if (refreshHoliday) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
                request.build()
            ).result.get(5, TimeUnit.SECONDS)
            // Wait only for durable handoff, never for the rendering itself.
        }
    }
}
