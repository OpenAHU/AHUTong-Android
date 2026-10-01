package com.ahu.ahutong.background

import android.content.Context
import android.content.Intent
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.core.storage.CourseReminderSettings
import com.ahu.ahutong.data.schedule.ScheduleReadModel
import com.ahu.ahutong.data.schedule.ScheduleHolidaySource
import com.ahu.ahutong.data.schedule.WidgetScheduleReadModel
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminderStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 后台组件的依赖入口。
 *
 * AppWidgetProvider、BroadcastReceiver 由系统拉起，没有可以注入的构造点；
 * 它们要的东西也不多，而且**都必须是只读的**：小组件只读课表快照，课程提醒只读两条提醒开关。
 * 组合根（:app）提供实现，方向仍然是 background → 端口。
 *
 * 节假日端口仅查询公开日历，不依赖教务登录，也不写教务数据。
 * 因此这里给的是窄接口，而不是 SettingsStore 与 ScheduleWeekConfig——后两者带着写入口，
 * 也带着"问一次远端"的能力。后台拿不到它们，就不可能顺手改用户设置或触发登录；门禁 R28 守着这条线。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundEntryPoint {
    fun courseReminderSettings(): CourseReminderSettings
    fun scheduleReadModel(): ScheduleReadModel
    fun scheduleHolidaySource(): ScheduleHolidaySource
    fun widgetScheduleReadModel(): WidgetScheduleReadModel
    /** 提醒要读学习通的作业与日程，也要写自己的记账——因此给的是窄视图，不是整个 Store。 */
    fun chaoxingReminderStore(): ChaoxingReminderStore
}

internal fun backgroundEntryPoint(context: Context): BackgroundEntryPoint =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        BackgroundEntryPoint::class.java
    )

/**
 * 无参版本：后台代码多半在"没有 Context 参数的静态位置"（对象里的私有函数、AlarmManager 回调），
 * 而应用上下文本来就装在一个地方（[AppEnvironmentHolder]），没必要一路传参。
 */
internal fun backgroundEntryPoint(): BackgroundEntryPoint =
    backgroundEntryPoint(AppEnvironmentHolder.context())

internal fun courseReminderSettings(): CourseReminderSettings =
    backgroundEntryPoint().courseReminderSettings()

internal fun scheduleReadModel(): ScheduleReadModel = backgroundEntryPoint().scheduleReadModel()

internal fun widgetScheduleReadModel(): WidgetScheduleReadModel = backgroundEntryPoint().widgetScheduleReadModel()

internal fun chaoxingReminderStore(): ChaoxingReminderStore =
    backgroundEntryPoint().chaoxingReminderStore()

/**
 * 打开应用：用包管理器拿启动 Intent，而不是引用某个 Activity 类——
 * 后台组件因此不必认识宿主里叫什么名字的那个入口。
 */
internal fun launchIntent(context: Context): Intent =
    context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)
