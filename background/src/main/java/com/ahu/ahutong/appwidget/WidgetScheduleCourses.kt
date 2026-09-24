package com.ahu.ahutong.appwidget

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean

/** 与课表页保持相同的显式周次规则，不能把未列出的同奇偶周推断为有课。 */
internal fun todayWidgetCourses(
    schedule: List<Course>,
    config: ScheduleConfigBean
): List<Course> {
    if (!config.isInSemester) return emptyList()
    return schedule
        .filter { config.week in it.weekIndexes && it.weekday == config.weekDay }
        .sortedBy { it.startTime }
}
