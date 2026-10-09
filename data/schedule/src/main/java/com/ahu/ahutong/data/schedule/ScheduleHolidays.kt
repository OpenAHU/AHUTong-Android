package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.Course
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

data class ScheduleHoliday(val name: String, val isOffDay: Boolean)

/** 国家放假安排仅用于显示提示，不修改教务课表或课程提醒。 */
interface ScheduleHolidaySource {
    /** 共享已校验的缓存；后台取得不同的新安排后统一更新标注。 */
    val holidays: StateFlow<Map<LocalDate, ScheduleHoliday>>

    /** 有缓存时立即返回并后台刷新；refresh=true 请求重新验证，仍有最小请求间隔。 */
    suspend fun load(
        years: Set<Int>,
        refresh: Boolean
    ): AhuResult<Map<LocalDate, ScheduleHoliday>>
}

fun scheduleHolidayLabel(holiday: ScheduleHoliday): String = if (holiday.isOffDay) "休" else "调"

fun scheduleHolidayNotice(holiday: ScheduleHoliday): String =
    "${holiday.name}${if (holiday.isOffDay) "放假" else "调休补班"} · 自动标注，是否上课及补课安排请自行确认"

fun scheduleWeekDates(startDate: LocalDate, weekCount: Int = 20): List<List<LocalDate>> =
    List(weekCount) { week -> List(7) { day -> startDate.plusDays((week * 7 + day).toLong()) } }

/** 次年公告可能包含本年十二月的调休安排，只在日期范围涉及十二月时额外查询。 */
fun scheduleHolidayYears(dates: List<List<LocalDate>>): Set<Int> = buildSet {
    dates.flatten().forEach {
        add(it.year)
        if (it.monthValue == 12) add(it.year + 1)
    }
}

fun hasHolidayCourses(
    courses: List<Course>,
    week: Int,
    dates: List<LocalDate>,
    holidays: Map<LocalDate, ScheduleHoliday>
): Boolean = courses.any { course ->
    week in course.weekIndexes &&
        dates.getOrNull(course.weekday - 1)?.let { holidays[it]?.isOffDay } == true
}
