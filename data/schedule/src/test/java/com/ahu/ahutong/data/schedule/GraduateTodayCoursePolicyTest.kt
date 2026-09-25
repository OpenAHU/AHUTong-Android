package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import org.junit.Assert.assertEquals
import org.junit.Test

class GraduateTodayCoursePolicyTest {
    private fun course(day: Int, start: Int, weeks: List<Int>) = Course().apply {
        name = "示例课程"
        setWeekday(day.toString())
        setStartTime(start.toString())
        setLength("2")
        setStartWeek(weeks.first().toString())
        setEndWeek(weeks.last().toString())
        weekIndexes = weeks
        clockRange = "08:50-10:35"
    }

    @Test fun mondayOfWeekThreeOnlyIncludesItsActualClassesInTimeOrder() {
        val courses = listOf(course(1, 8, listOf(3, 5)), course(2, 2, listOf(3)),
            course(1, 4, listOf(3)), course(1, 2, listOf(2, 4, 6)))
        assertEquals(listOf(4, 8),
            GraduateTodayCoursePolicy.filter(courses, teachingWeek = 3, weekday = 1).map { it.startTime })
        assertEquals(1, GraduateTodayCoursePolicy.filter(courses, teachingWeek = 3, weekday = 2).size)
    }

    @Test fun missingWeeksNeverUseUndergraduateParityFallback() {
        val courses = listOf(course(5, 2, listOf(2, 6)), course(5, 4, listOf(3)))
        assertEquals(emptyList<Course>(), GraduateTodayCoursePolicy.filter(courses, 4, 5))
    }
}
