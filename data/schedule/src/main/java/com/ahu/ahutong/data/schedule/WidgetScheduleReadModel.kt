package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean

/** Desktop-only snapshot. Reminder scheduling keeps its existing undergraduate read model. */
interface WidgetScheduleReadModel {
    fun cachedSnapshot(): WidgetScheduleSnapshot
}

data class WidgetScheduleSnapshot(
    val courses: List<Course>,
    val config: ScheduleConfigBean,
    val fetchedAt: Long? = null,
    val unavailableMessage: String? = null,
    val notice: String? = null
)
