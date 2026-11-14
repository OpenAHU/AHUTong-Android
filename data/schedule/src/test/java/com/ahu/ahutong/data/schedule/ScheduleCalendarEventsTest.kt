package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.Exam
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleCalendarEventsTest {
    @Test
    fun `course window follows Monday boundaries and the exact clock range`() {
        val firstWeek = LocalDate.of(2026, 9, 7)
        val course = Course().apply {
            name = "自定义课"
            clockRange = "12:30-13:05"
            setWeekday("7")
            weekIndexes = listOf(1, 2, 4, 5)
        }

        val dates = courseCalendarEvents(listOf(course, course), firstWeek, firstWeek.plusDays(8))

        assertEquals(listOf("2026-09-20T12:30", "2026-10-04T12:30"), dates.map { it.start.toString() })
        assertEquals(listOf("2026-09-20T13:05", "2026-10-04T13:05"), dates.map { it.end.toString() })
        assertEquals(setOf(15), dates.map { it.reminderMinutes }.toSet())
    }

    @Test
    fun `exam events skip invalid and finished records and retain dates outside the course window`() {
        val exams = listOf(
            exam("有效", "2026-12-01 9:00 ～ 11:30"),
            exam("未公布结束时间", "2026-12-01 09:00"),
            exam("不存在的日期", "2026-11-31 09:00~11:00"),
            exam("错误的时间顺序", "2026-12-01 11:00~09:00"),
            exam("已结束", "2026-12-01 09:00~11:00", finished = true),
            exam("过期", "2026-09-07 09:00~11:00")
        )

        val event = examCalendarEvents(exams, LocalDateTime.parse("2026-09-14T12:00")).single()
        assertEquals("考试 · 有效", event.title)
        assertEquals(LocalDateTime.parse("2026-12-01T09:00"), event.start)
        assertEquals(LocalDateTime.parse("2026-12-01T11:30"), event.end)
        assertEquals(30, event.reminderMinutes)
    }

    private fun exam(name: String, time: String, finished: Boolean = false) = Exam().apply {
        course = name
        this.time = time
        this.finished = finished
    }
}
