package com.ahu.ahutong.data.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleVisibleWeekdaysTest {
    @Test
    fun weekdaysStayVisibleRegardlessOfWeekendToggles() {
        assertEquals(listOf(1, 2, 3, 4, 5), scheduleVisibleWeekdays(false, false))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), scheduleVisibleWeekdays(true, false))
        assertEquals(listOf(1, 2, 3, 4, 5, 7), scheduleVisibleWeekdays(false, true))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), scheduleVisibleWeekdays(true, true))
    }
}
