package com.ahu.ahutong.data.schedule

import android.content.Context
import com.ahu.ahutong.core.common.AppEnvironmentHolder

/**
 * 课表页的界面偏好（轻量 UI 开关，不进配置 Bean、不参与课表数据语义）。
 *
 * 经 [AppEnvironmentHolder] 取 Context，与数据层其他存储同一套路；
 * feature/schedule 直接读写，不反向依赖 :app。
 */
object ScheduleUiPrefs {

    private const val PREFS = "schedule_ui"
    private const val KEY_SHOW_NOW_TIMELINE = "show_now_timeline"
    private const val KEY_HIDE_WEEKENDS = "hide_weekends"

    private val prefs
        get() = AppEnvironmentHolder.context().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 是否在课表上绘制"当前时间"指示线。默认关——时间线是增量信息，用户按需打开。 */
    fun isNowTimelineVisible(): Boolean = prefs.getBoolean(KEY_SHOW_NOW_TIMELINE, false)

    fun setNowTimelineVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NOW_TIMELINE, visible).apply()
    }

    fun areWeekendsHidden(): Boolean = prefs.getBoolean(KEY_HIDE_WEEKENDS, false)

    fun setWeekendsHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_WEEKENDS, hidden).apply()
    }
}
