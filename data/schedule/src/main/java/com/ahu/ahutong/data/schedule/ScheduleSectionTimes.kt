package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course

/**
 * 课表的节次时间表，以及「第几节到第几节」换算成钟点区间。
 *
 * 这些原来挂在 ScheduleViewModel 的伴生对象上，但它的使用者远比课表页多：小组件、课前提醒
 * 与首页的时间线都在用它判断「正在上哪一节」。放在数据模块之后，这些消费者不再需要认识任何
 * 一个 ViewModel，课表 feature 搬走时也不会把它们一起拖走。
 *
 * 纯数据 + 纯函数，因此能带着自己的测试待在模块里（见 ScheduleTimeRangeTest）。
 */
object ScheduleSectionTimes {

    val timetable by lazy {
        mapOf(
            1 to "08:00-08:45",
            2 to "08:50-09:35",
            3 to "09:50-10:35",
            4 to "10:40-11:25",
            5 to "11:30-12:15",
            6 to "14:00-14:45",
            7 to "14:50-15:35",
            8 to "15:50-16:35",
            9 to "16:40-17:25",
            10 to "17:30-18:15",
            11 to "19:00-19:45",
            12 to "19:50-20:35",
            13 to "20:40-21:25"
        )
    }

    /** Pre-parsed once because the home timeline reads these ranges during composition. */
    private val timetableMinuteRanges by lazy {
        timetable.mapValues { (_, range) ->
            parseClockMinutes(range.substringBefore('-'))..
                parseClockMinutes(range.substringAfter('-'))
        }
    }

    private fun parseClockMinutes(clock: String): Int {
        val separator = clock.indexOf(':')
        require(separator > 0 && separator < clock.lastIndex) { "Invalid clock: $clock" }
        return clock.substring(0, separator).toInt() * 60 +
            clock.substring(separator + 1).toInt()
    }

    /** 课程的钟表时间范围，如 "08:00-09:35"；节次未知时返回 null。 */
    fun getCourseClockRange(course: Course): String? {
        course.clockRange?.takeIf { it.isNotBlank() }?.let { return it }
        if (course.length <= 0) return null
        val first = timetable[course.startTime] ?: return null
        val last = timetable[course.startTime + course.length - 1] ?: return null
        return "${first.substringBefore('-')}-${last.substringAfter('-')}"
    }

    fun getCourseTimeRangeInMinutes(course: Course): IntRange {
        course.clockRange?.split('-')?.takeIf { it.size == 2 }?.let { parts ->
            runCatching { return parseClockMinutes(parts[0])..parseClockMinutes(parts[1]) }
        }
        val startSection = course.startTime
        val sectionCount = course.length
        if (sectionCount <= 0) return IntRange.EMPTY
        val firstSection = timetableMinuteRanges[startSection] ?: return IntRange.EMPTY
        val lastSection = timetableMinuteRanges[startSection + sectionCount - 1] ?: return IntRange.EMPTY
        return firstSection.first..lastSection.last
    }
}
