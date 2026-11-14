package com.ahu.ahutong.ui.screen.main.schedule

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.ExamTimeRange
import com.ahu.ahutong.data.schedule.CustomCourseMath
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val EXAM_COURSE_ID = "exam"
private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", Locale.SIMPLIFIED_CHINESE)
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

internal fun Course.isExam(): Boolean = courseId == EXAM_COURSE_ID

internal fun scheduleExamCourses(
    exams: List<Exam>,
    semesterStart: LocalDate
): List<Course> = exams.mapNotNull { exam ->
    val name = exam.course?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    val time = ExamTimeRange.parse(exam.time) ?: return@mapNotNull null
    val date = time.start.toLocalDate()
    val days = ChronoUnit.DAYS.between(semesterStart, date)
    if (days !in 0 until 52L * 7) return@mapNotNull null
    val week = (days / 7).toInt() + 1
    val span = CustomCourseMath.snapToSections(
        time.start.hour * 60 + time.start.minute,
        time.end.hour * 60 + time.end.minute
    )
    Course().apply {
        courseId = EXAM_COURSE_ID
        this.name = "考试 · $name"
        location = exam.location
        clockRange = "${time.start.format(clockFormat)}-${time.end.format(clockFormat)}"
        extra = listOfNotNull(
            date.format(dateFormat),
            exam.seatNum?.takeIf { it.isNotBlank() }?.let { "座位号 $it" }
        ).joinToString("\n")
        setWeekday((days % 7 + 1).toString())
        setStartWeek(week.toString())
        setEndWeek(week.toString())
        weekIndexes = listOf(week)
        setStartTime(span.startSection.toString())
        setLength(span.length.toString())
    }
}.distinct()
