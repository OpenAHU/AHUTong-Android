package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.ExamTimeRange
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

internal val schoolCalendarZone: ZoneId = ZoneId.of("Asia/Shanghai")

internal data class ScheduleCalendarEvent(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String,
    val description: String,
    val reminderMinutes: Int
)

internal fun mondayOf(date: LocalDate): LocalDate = date.minusDays(date.dayOfWeek.value - 1L)

internal fun courseCalendarEvents(
    courses: List<Course>,
    semesterStart: LocalDate,
    today: LocalDate
): Set<ScheduleCalendarEvent> = buildSet {
    val from = mondayOf(today)
    val until = from.plusWeeks(3)
    for (course in courses) {
        val title = course.name?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val weekday = course.weekday
        if (weekday !in 1..7) continue
        val minutes = ScheduleSectionTimes.getCourseTimeRangeInMinutes(course)
        if (minutes.isEmpty() || minutes.first !in 0..1439 || minutes.last !in (minutes.first + 1)..1439) continue
        for (week in course.weekIndexes) {
            if (week < 1) continue
            val date = semesterStart.plusWeeks(week - 1L).plusDays(weekday - 1L)
            if (date < from || date >= until) continue
            add(ScheduleCalendarEvent(
                title = title,
                start = date.atStartOfDay().plusMinutes(minutes.first.toLong()),
                end = date.atStartOfDay().plusMinutes(minutes.last.toLong()),
                location = course.location.orEmpty(),
                description = course.teacher?.takeIf { it.isNotBlank() }?.let { "教师 $it" }.orEmpty(),
                reminderMinutes = 15
            ))
        }
    }
}

internal fun examCalendarEvents(exams: List<Exam>, now: LocalDateTime): Set<ScheduleCalendarEvent> =
    exams.mapNotNullTo(mutableSetOf()) { exam ->
        val name = exam.course?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNullTo null
        val time = ExamTimeRange.parse(exam.time) ?: return@mapNotNullTo null
        if (exam.finished == true || time.end <= now) return@mapNotNullTo null
        ScheduleCalendarEvent(
            title = "考试 · $name",
            start = time.start,
            end = time.end,
            location = exam.location.orEmpty(),
            description = exam.seatNum?.takeIf { it.isNotBlank() }?.let { "座位号 $it" }.orEmpty(),
            reminderMinutes = 30
        )
    }
