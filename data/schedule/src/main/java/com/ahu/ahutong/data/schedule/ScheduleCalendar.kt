package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.ScheduleConfigBean
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

/** Rebase a loaded calendar without changing the snapshot shared by other screens. */
fun ScheduleConfigBean.forDate(
    date: LocalDate,
    weekCount: Int = TeachingWeekPolicy.SCHEDULE_WEEK_COUNT,
    zoneId: ZoneId = ZoneId.systemDefault()
): ScheduleConfigBean {
    require(weekCount > 0)
    val firstDay = startTime?.toInstant()?.atZone(zoneId)?.toLocalDate()
    val actualWeek = firstDay?.let {
        (Math.floorDiv(ChronoUnit.DAYS.between(it, date), 7L) + 1L).toInt()
    } ?: week
    return ScheduleConfigBean().also {
        it.startTime = startTime?.let { start -> Date(start.time) }
        it.week = if (firstDay != null) actualWeek.coerceIn(1, weekCount) else week
        it.weekDay = date.dayOfWeek.value
        it.isShowAll = isShowAll
        it.isInSemester = isInSemester && actualWeek in 1..weekCount
    }
}
