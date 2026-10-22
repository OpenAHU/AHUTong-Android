package com.ahu.ahutong.ui.screen.main.home

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes

internal data class HomeCourseSelection(
    val today: List<Course>,
    val strip: List<Course>,
    val isTomorrow: Boolean
)

/** Both academic systems provide exact teaching weeks; missing weeks are never inferred. */
internal fun selectHomeCourses(
    undergraduateCourses: List<Course>,
    graduateCourses: List<Course>,
    undergraduateEnabled: Boolean,
    config: ScheduleConfigBean?,
    currentMinutes: Int
): HomeCourseSelection {
    if (config == null || !config.isInSemester) {
        return HomeCourseSelection(emptyList(), emptyList(), false)
    }
    val courses = if (undergraduateEnabled) undergraduateCourses else graduateCourses
    fun coursesOn(week: Int, weekday: Int) = courses.filter {
        week in it.weekIndexes && it.weekday == weekday
    }.sortedBy { it.startTime }

    val today = coursesOn(config.week, config.weekDay)
    val allDone = today.isNotEmpty() && today.all {
        val range = ScheduleSectionTimes.getCourseTimeRangeInMinutes(it)
        !range.isEmpty() && currentMinutes > range.last
    }
    if (!allDone) return HomeCourseSelection(today, today, false)

    val nextWeek = if (config.weekDay == 7) config.week + 1 else config.week
    val nextWeekday = if (config.weekDay == 7) 1 else config.weekDay + 1
    return HomeCourseSelection(today, coursesOn(nextWeek, nextWeekday), true)
}
