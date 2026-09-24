package com.ahu.ahutong.appwidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/** 桌面刷新入口：组件先画缓存，网络请求交给可在进程退出后继续运行的 Worker。 */
class WidgetScheduleRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetUpdateScheduler.ACTION_REFRESH_SCHEDULE) return

        val manager = AppWidgetManager.getInstance(context)
        val hasGlance = manager.getAppWidgetIds(
            ComponentName(context, ScheduleAppWidgetReceiver::class.java)
        ).isNotEmpty()
        val hasAdaptive = manager.getAppWidgetIds(
            ComponentName(context, ScheduleAdaptiveWidgetProvider::class.java)
        ).isNotEmpty()
        if (!hasGlance && !hasAdaptive) return

        val requestBuilder = OneTimeWorkRequestBuilder<WidgetScheduleRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        // Android 11 及更早版本的加急任务需要前台通知；普通任务避免刷新时打扰用户。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val request = requestBuilder.build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widget-schedule-refresh",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
