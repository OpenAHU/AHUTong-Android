package com.ahu.ahutong.appwidget

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetScheduleCoursesTest {
    @Test
    fun `an omitted week does not show a course with the same parity`() {
        val course = Course().apply {
            weekIndexes = listOf(1, 5)
            setWeekday("1")
            setStartTime("2")
        }
        val config = ScheduleConfigBean().apply {
            week = 3
            weekDay = 1
        }

        assertEquals(emptyList<Course>(), todayWidgetCourses(listOf(course), config))

        config.week = 5
        assertEquals(listOf(course), todayWidgetCourses(listOf(course), config))

        config.setInSemester(false)
        assertEquals(emptyList<Course>(), todayWidgetCourses(listOf(course), config))
    }
}
