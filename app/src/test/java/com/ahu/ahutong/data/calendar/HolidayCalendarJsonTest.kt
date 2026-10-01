package com.ahu.ahutong.data.calendar

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HolidayCalendarJsonTest {
    private fun json(days: String) = """{"year":2026,"papers":["https://www.gov.cn/notice"],"days":[$days]}"""
    private fun day(date: String, isOffDay: String = "true") =
        """{"name":"国庆节","date":"$date","isOffDay":$isOffDay}"""

    @Test
    fun `network response distinguishes holidays from adjusted workdays`() {
        val days = (1..7).map { day("2026-10-0$it") } +
            listOf(day("2026-10-10", "false"), day("2026-09-20", "false"))
        val calendar = parseHolidayCalendar(json(days.joinToString(",")), 2026)
        assertEquals(7, calendar.values.count { it.isOffDay })
        (1..7).forEach { assertTrue(calendar.getValue(LocalDate.of(2026, 10, it)).isOffDay) }
        assertFalse(calendar.getValue(LocalDate.of(2026, 10, 10)).isOffDay)
        assertFalse(calendar.getValue(LocalDate.of(2026, 9, 20)).isOffDay)
    }

    @Test
    fun `invalid or placeholder responses cannot be accepted as holiday data`() {
        assertFails { parseHolidayCalendar("<html>unavailable</html>", 2026) }
        assertFails { parseHolidayCalendar(json(""), 2026) }
        assertFails { parseHolidayCalendar(json(day("2026-10-01")), 2027) }
        assertFails { parseHolidayCalendar(json(day("2026-02-30")), 2026) }
        assertFails { parseHolidayCalendar(json(day("2026-10-01", "\"false\"")), 2026) }
        assertFails { parseHolidayCalendar(json(day("2026-10-01") + "," + day("2026-10-01")), 2026) }
        assertFails { parseHolidayCalendar(json(day("2025-10-01")), 2026) }
    }

    @Test
    fun `official cross year dates are allowed`() {
        val calendar = parseHolidayCalendar(json(day("2025-12-31") + "," + day("2027-01-01")), 2026)
        assertEquals(2, calendar.size)
    }
}
