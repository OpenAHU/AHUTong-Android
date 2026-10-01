package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleHolidaysTest {
    @Test
    fun `semester dates retain the year when crossing new year`() {
        val dates = scheduleWeekDates(LocalDate.of(2026, 12, 28), 2)
        assertEquals(LocalDate.of(2027, 1, 1), dates[0][4])
        assertEquals(LocalDate.of(2027, 1, 10), dates[1][6])
        assertEquals(setOf(2026, 2027), scheduleHolidayYears(dates))
        assertEquals(setOf(2026), scheduleHolidayYears(scheduleWeekDates(LocalDate.of(2026, 2, 23))))
    }

    @Test
    fun `notice requires a course taught that week on an explicitly off day`() {
        val dates = scheduleWeekDates(LocalDate.of(2026, 9, 28), 2)
        val holidays = mapOf(
            LocalDate.of(2026, 10, 1) to ScheduleHoliday("国庆节", true),
            LocalDate.of(2026, 10, 10) to ScheduleHoliday("国庆节", false)
        )
        val thursday = Course().apply { setWeekday("4"); weekIndexes = listOf(1) }
        val saturday = Course().apply { setWeekday("6"); weekIndexes = listOf(1, 2) }
        assertTrue(hasHolidayCourses(listOf(thursday), 1, dates[0], holidays))
        thursday.weekIndexes = listOf(2)
        assertFalse(hasHolidayCourses(listOf(thursday), 1, dates[0], holidays))
        thursday.weekIndexes = listOf(1)
        assertFalse(hasHolidayCourses(listOf(thursday), 2, dates[1], holidays))
        assertFalse(hasHolidayCourses(listOf(saturday), 1, dates[0], holidays))
        assertFalse(hasHolidayCourses(listOf(saturday), 2, dates[1], holidays))
        assertFalse(hasHolidayCourses(listOf(thursday), 1, emptyList(), holidays))
        val nextYear = scheduleWeekDates(LocalDate.of(2027, 9, 27), 1)[0]
        assertFalse(hasHolidayCourses(listOf(thursday), 1, nextYear, holidays))
    }
}
