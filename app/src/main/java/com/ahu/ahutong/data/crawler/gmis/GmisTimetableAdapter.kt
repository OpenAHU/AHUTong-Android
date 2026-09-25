package com.ahu.ahutong.data.crawler.gmis

import com.ahu.ahutong.data.model.Course

data class GmisScheduleGridData(
    val courses: List<Course>,
    val unplaced: List<GmisCourse>,
    val timetable: Map<Int, String>,
    val weekCount: Int
)

/** Adapt GMIS data to the existing weekly timetable renderer without inventing dates or periods. */
object GmisTimetableAdapter {
    fun adapt(source: GmisTimetable): GmisScheduleGridData {
        val (placed, unplaced) = source.courses.partition {
            it.startSection != null && it.endSection != null && !it.weeks.isNullOrEmpty()
        }
        val periods = source.sections.associate { it.number to it.clock.orEmpty() }
        val lastPeriod = maxOf(14, periods.keys.maxOrNull() ?: 14)
        val courses = placed.map { original ->
            val weeks = original.weeks.orEmpty().sorted()
            Course().apply {
                name = original.name
                teacher = original.teacher
                location = original.location
                extra = original.details
                setWeekday(original.weekday.toString())
                setStartTime(original.startSection.toString())
                setLength((original.endSection!! - original.startSection!! + 1).toString())
                setStartWeek(weeks.first().toString())
                setEndWeek(weeks.last().toString())
                weekIndexes = weeks
            }
        }
        return GmisScheduleGridData(
            courses = courses,
            unplaced = unplaced,
            timetable = (1..lastPeriod).associateWith { periods[it].orEmpty() },
            weekCount = maxOf(20, source.maxWeek)
        )
    }
}
