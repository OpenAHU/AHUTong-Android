package com.ahu.ahutong.data.crawler.gmis

import com.ahu.ahutong.data.schedule.gmis.*

import com.ahu.ahutong.ui.screen.main.schedule.courseScheduleDescription
import com.ahu.ahutong.ui.screen.main.schedule.schedulePeriodDescription
import org.junit.Assert.*
import org.junit.Test

class GmisTimetableAdapterTest {
    private val course = GmisCourse(
        "示例课程", "示例教师", "示例教室", 2, 2, 5,
        setOf(2, 3, 4, 5), "2-5周", "08:50-12:15", ""
    )

    @Test fun adaptsGraduateCoursesToExistingCourseCardsWithoutChangingSpansOrWeeks() {
        val grid = GmisTimetableAdapter.adapt(GmisTimetable(listOf(course), emptyList()))
        val mapped = grid.courses.single()
        assertEquals("示例课程", mapped.name)
        assertEquals(2, mapped.weekday)
        assertEquals(2, mapped.startTime)
        assertEquals(4, mapped.length)
        assertEquals(listOf(2, 3, 4, 5), mapped.weekIndexes)
        assertEquals("示例教师", mapped.teacher)
        assertTrue(grid.unplaced.isEmpty())
    }

    @Test fun sharedCourseAccessibilityUsesGmisFourteenthPeriodRatherThanUndergraduateTimes() {
        val last = course.copy(startSection = 14, endSection = 14, clock = "21:30-22:15")
        val grid = GmisTimetableAdapter.adapt(GmisTimetable(
            listOf(last), listOf(GmisSection(14, "晚上", "21:30-22:15"))
        ))
        assertEquals(14, grid.timetable.size)
        val description = courseScheduleDescription(grid.courses.single(), grid.timetable)
        assertTrue(description.contains("第14节"))
        assertTrue(description.contains("21:30至22:15"))
        assertFalse(description.contains("月"))
    }

    @Test fun untimedAndUnknownWeekCoursesAreKeptOutsideTheGridRatherThanInventingPositions() {
        val untimed = course.copy(startSection = null, endSection = null)
        val unknownWeeks = course.copy(weeks = null)
        val grid = GmisTimetableAdapter.adapt(GmisTimetable(listOf(untimed, unknownWeeks), emptyList()))
        assertTrue(grid.courses.isEmpty())
        assertEquals(listOf(untimed, unknownWeeks), grid.unplaced)
    }

    @Test fun weeksBeyondTwentyRemainSelectable() {
        val grid = GmisTimetableAdapter.adapt(GmisTimetable(listOf(course.copy(weeks = setOf(23, 24))), emptyList()))
        assertEquals(24, grid.weekCount)
        assertEquals(listOf(23, 24), grid.courses.single().weekIndexes)
    }

    @Test fun missingSectionClockIsDescribedWithoutInventingATime() {
        assertEquals("第14节，时间待确认", schedulePeriodDescription(14, ""))
    }
}
