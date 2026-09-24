package com.ahu.ahutong.ui.state

import android.content.Context
import android.content.Intent
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.core.common.CourseReminderControl
import com.ahu.ahutong.notification.CourseReminderCapability
import com.ahu.ahutong.notification.CourseReminderNotifier
import com.ahu.ahutong.notification.CourseReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CourseReminderControl] 的生产实现：把设置页的命令转给提醒调度与通知。
 *
 * Context 从 [AppEnvironmentHolder] 取，与 AndroidRepositoryFileAccess 同一模式；
 * 代码与迁移前逐字一致，只是从界面搬到了适配器里。
 */
@Singleton
class AndroidCourseReminderControl @Inject constructor() : CourseReminderControl {

    private val context: Context get() = AppEnvironmentHolder.context()

    override fun reschedule() {
        CourseReminderScheduler.reschedule(context)
    }

    override fun cancel() {
        CourseReminderScheduler.cancel(context)
    }

    override fun cancelActiveReminder() {
        CourseReminderNotifier.cancelActiveReminder(context)
    }

    /**
     * 与迁移前逐字一致：先试岛卡专用页，起不来再回落到通知设置页。
     * 回落页若也起不来，异常照旧抛出——不把"设置页打不开"伪装成成功。
     */
    override fun openSystemSettings() {
        val promotionIntent = CourseReminderCapability.createPromotionSettingsIntent(context)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallbackIntent = CourseReminderCapability.createNotificationSettingsIntent(context)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(promotionIntent) }
            .getOrElse { context.startActivity(fallbackIntent) }
    }
}
