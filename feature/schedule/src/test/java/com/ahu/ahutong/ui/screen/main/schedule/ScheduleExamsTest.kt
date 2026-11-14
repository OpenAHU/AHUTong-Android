package com.ahu.ahutong.ui.screen.main.schedule

import com.ahu.ahutong.data.model.Exam
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleExamsTest {
    @Test
    fun `exam appears only on the matching timetable week and keeps its exact time`() {
        val exam = Exam().apply {
            course = "大学物理"
            time = "2026-09-20 09:30~11:15"
            location = "教室 A"
            seatNum = "07"
        }

        val courses = scheduleExamCourses(listOf(exam), LocalDate.of(2026, 9, 7))
        val card = weeklyCourseGroups(courses, 2).single().single()
        assertTrue(weeklyCourseGroups(courses, 1).isEmpty())
        assertTrue(card.isExam())
        assertEquals(7, card.weekday)
        assertEquals("09:30-11:15", card.clockRange)
        assertEquals("2026-09-20 星期日\n座位号 07", card.extra)
    }
}
