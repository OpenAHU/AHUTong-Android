package com.ahu.ahutong.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.ahu.ahutong.R
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.ui.state.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.Locale


class ScheduleAdaptiveWidgetProvider : AppWidgetProvider() {

    val TAG = this.javaClass.simpleName

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            } else {
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, ScheduleAdaptiveWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, ids)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Only cancel if no other widgets need it? 
        // Actually Scheduler handles updates for BOTH, so if one is disabled but other is active, we shouldn't cancel globally.
        // But for simplicity, if this provider is disabled (last widget removed), we might cancel.
        // However, ScheduleAppWidgetReceiver also manages it. 
        // A safer approach: Scheduler runs if EITHER is active. 
        // But Scheduler.cancel() cancels the pending intent which is shared.
        // We should check if any widgets exist.
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val glanceIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ScheduleAppWidgetReceiver::class.java))
        if (glanceIds.isEmpty()) {
             WidgetUpdateScheduler.cancel(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.e(TAG, "updateAppWidget: 小组件刷新", )
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val isLarge = minWidth >= 300
        val layoutRes = if (isLarge) {
            R.layout.layout_adaptive_test_large
        } else {
            R.layout.layout_adaptive_test_small
        }
        val titleId = if (isLarge) {
            R.id.adaptive_test_title_large
        } else {
            R.id.adaptive_test_title_small
        }
        val subtitleId = if (isLarge) {
            R.id.adaptive_test_subtitle_large
        } else {
            R.id.adaptive_test_subtitle_small
        }
        val itemsContainerId = if (isLarge) {
            R.id.adaptive_test_items_large
        } else {
            R.id.adaptive_test_items_small
        }
        // AppWidgetProvider callbacks run on the broadcast thread. Keep this path cache-only;
        // remote semester synchronization is handled by the app and the suspend Glance widget.
        val scheduleConfig = CurrentWeekResolver.resolveLocalConfig()?.config
            ?: CurrentWeekResolver.defaultConfig().config
        val currentWeek = scheduleConfig.week
        val weekDay = scheduleConfig.weekDay
        val undergraduateEnabled = AHUCache.canUseUndergraduateAcademics()
        val schedule = AHUCache.getSchoolTerm()
            .takeIf { undergraduateEnabled }
            ?.let(AHUCache::getSchedule)
            .orEmpty()
        val currentMinutes = DebugClock.currentMinutes()
        val todayCourses = if (scheduleConfig.isInSemester) {
            schedule
                .filter { currentWeek in it.startWeek..it.endWeek }
                .filter { it.weekday == weekDay }
                .filter {
                    if (currentWeek in it.weekIndexes) {
                        true
                    } else {
                        currentWeek % 2 == it.startWeek % 2
                    }
                }
                .sortedBy { it.startTime }
        } else {
            emptyList()
        }
        val remainingCourses = todayCourses.filter {
            currentMinutes <= ScheduleViewModel.getCourseTimeRangeInMinutes(it).last
        }

        val displayCourses = remainingCourses
        val widgetColors = resolve(context)
        val titleText: String
        val subtitleText: String
        if (!undergraduateEnabled) {
            titleText = "研究生账号"
            subtitleText = "暂不支持本科课表微件"
        } else if (displayCourses.isEmpty()) {
            titleText = "没课啦🎉"
            subtitleText = SimpleDateFormat("MM-dd/EE", Locale.CHINA).format(DebugClock.nowDate())
        } else {
            titleText = "还剩 ${remainingCourses.size} 节"
            subtitleText = SimpleDateFormat("MM-dd/EE", Locale.CHINA).format(DebugClock.nowDate())
        }
        val remoteViews = RemoteViews(context.packageName, layoutRes)
        remoteViews.setOnClickPendingIntent(
            R.id.layout_wight,
            createRefreshPendingIntent(context, appWidgetId)
        )
        remoteViews.setTextViewText(titleId, titleText)
        remoteViews.setTextViewText(subtitleId, subtitleText)
        remoteViews.setTextColor(titleId, widgetColors.primaryText.toArgb())
        remoteViews.setTextColor(subtitleId, widgetColors.secondaryText.toArgb())
        remoteViews.removeAllViews(itemsContainerId)
        setRemoteViewBackgroundColor(
            remoteViews,
            R.id.layout_wight,
            widgetColors.background.toArgb()
        )
        if (displayCourses.isEmpty()) {
            val emptyItem = RemoteViews(context.packageName, R.layout.layout_widget_item)
            emptyItem.setViewVisibility(R.id.little_circle, View.GONE)
            emptyItem.setTextViewText(R.id.course_name_tv, if (undergraduateEnabled) "暂无课程" else "本科课表已停用")
            emptyItem.setViewVisibility(R.id.course_time_tv, View.GONE)
            emptyItem.setTextViewText(R.id.course_location_tv, if (undergraduateEnabled) "点击打开安大通查看完整课表" else "智慧安大服务仍可使用")
            emptyItem.setTextColor(R.id.little_circle, widgetColors.off.toArgb())
            emptyItem.setTextColor(R.id.course_name_tv, widgetColors.primaryText.toArgb())
            emptyItem.setTextColor(R.id.course_time_tv, widgetColors.secondaryText.toArgb())
            emptyItem.setTextColor(R.id.course_location_tv, widgetColors.secondaryText.toArgb())
            setRemoteViewBackgroundColor(
                emptyItem,
                R.id.widget_item_color_bg,
                Color.TRANSPARENT
            )
            remoteViews.addView(itemsContainerId, emptyItem)
        } else {
            displayCourses.forEach {
                val currentRange = ScheduleViewModel.getCourseTimeRangeInMinutes(it)
                val isPassed = currentMinutes > currentRange.first
                val isOngoing = currentMinutes in currentRange
                val item = RemoteViews(context.packageName, R.layout.layout_widget_item)
                item.setTextViewText(R.id.little_circle, if (isPassed) "●" else "○")
                item.setTextViewText(R.id.course_name_tv, it.name)
                item.setTextViewText(
                    R.id.course_time_tv,
                    "${it.startTime}-${it.startTime + it.length - 1}节"
                )
                item.setTextViewText(R.id.course_location_tv, shortenLocation(it.location))
                item.setTextColor(
                    R.id.little_circle,
                    if (isPassed) widgetColors.on.toArgb() else widgetColors.off.toArgb()
                )
                item.setTextColor(
                    R.id.course_name_tv,
                    if (isOngoing) widgetColors.ongoingPrimaryText.toArgb() else widgetColors.primaryText.toArgb()
                )
                item.setTextColor(
                    R.id.course_time_tv,
                    if (isOngoing) widgetColors.ongoingSecondaryText.toArgb() else widgetColors.secondaryText.toArgb()
                )
                item.setTextColor(
                    R.id.course_location_tv,
                    if (isOngoing) widgetColors.ongoingSecondaryText.toArgb() else widgetColors.secondaryText.toArgb()
                )

                setRemoteViewBackgroundColor(
                    item,
                    R.id.widget_item_color_bg,
                    if (isOngoing) widgetColors.activatedRow.toArgb() else Color.TRANSPARENT
                )
                remoteViews.addView(itemsContainerId, item)
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    private fun setRemoteViewBackgroundColor(
        remoteViews: RemoteViews,
        viewId: Int,
        color: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            remoteViews.setColorStateList(
                viewId,
                "setBackgroundTintList",
                ColorStateList.valueOf(color)
            )
        } else {
            remoteViews.setInt(viewId, "setBackgroundColor", color)
        }
    }

    private fun createRefreshPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, ScheduleAdaptiveWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, appWidgetId, intent, flags)
    }

    companion object {
        private const val ACTION_REFRESH = "com.ahu.ahutong.appwidget.ACTION_REFRESH_SCHEDULE"
    }
}

private val LOCATION_SHORTEN_MAP = mapOf(
    "博学北楼" to "博北",
    "博学南楼" to "博南",
    "笃行北楼" to "笃北",
    "笃行南楼" to "笃南",
    "互联大楼" to "互楼",
    "体育场" to "体"
)

private val LOCATION_PATTERN =
    Regex(LOCATION_SHORTEN_MAP.keys.joinToString("|") { Regex.escape(it) })

private fun shortenLocation(location: String?): String =
    (location ?: "").replace(LOCATION_PATTERN) { LOCATION_SHORTEN_MAP[it.value].orEmpty() }
