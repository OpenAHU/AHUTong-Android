package com.ahu.ahutong.ui.screen.main.schedule

import com.ahu.ahutong.data.model.Course

private val accessibleWeekdays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

internal fun scheduleDayDescription(weekday: Int, date: String? = null): String {
    val day = accessibleWeekdays.getOrNull(weekday - 1) ?: "星期未知"
    val parts = date?.split('-').orEmpty()
    val month = parts.getOrNull(0)?.toIntOrNull()
    val dayOfMonth = parts.getOrNull(1)?.toIntOrNull()
    return if (month != null && month in 1..12 && dayOfMonth != null && dayOfMonth in 1..31) {
        "$day，${month}月${dayOfMonth}日"
    } else {
        day
    }
}

internal fun schedulePeriodDescription(section: Int, time: String): String =
    "第${section}节，${time.substringBefore('-')}至${time.substringAfter('-')}"

internal fun courseScheduleDescription(
    course: Course,
    timetable: Map<Int, String>,
    date: String? = null,
    includeWeeks: Boolean = false
): String = buildList {
    add(course.name.orEmpty().ifBlank { "未命名课程" })
    add(scheduleDayDescription(course.weekday, date))
    if (includeWeeks) add(course.weekRangeText())
    val firstSection = course.startTime
    val lastSection = firstSection + course.length - 1
    add(if (firstSection == lastSection) "第${firstSection}节" else "第${firstSection}至${lastSection}节")
    val start = timetable[firstSection]?.substringBefore('-')
    val end = timetable[lastSection]?.substringAfter('-')
    if (start != null && end != null) add("${start}至${end}")
    course.location?.takeIf { it.isNotBlank() }?.let(::add)
}.joinToString("，")
