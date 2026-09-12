package com.ahu.ahutong.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolCalendarYearPolicyTest {
    @Test
    fun normalizeKeepsOnlyValidExistingAcademicYearIdentifiers() {
        assertEquals(
            listOf("2026-2027", "2025-2026", "2024-2025"),
            SchoolCalendarYearPolicy.normalize(
                listOf(
                    "2025-2026",
                    "2026-2027",
                    "invalid",
                    "2025-2027",
                    "2024-2025",
                    "2026-2027"
                )
            )
        )
    }

    @Test
    fun selectPrefersCurrentThenServerLatestThenNewestAvailable() {
        val years = listOf("2026-2027", "2025-2026")

        assertEquals(
            "2025-2026",
            SchoolCalendarYearPolicy.select(years, "2026-2027", "2025-2026")
        )
        assertEquals(
            "2026-2027",
            SchoolCalendarYearPolicy.select(years, "2026-2027", null)
        )
        assertEquals(
            "2026-2027",
            SchoolCalendarYearPolicy.select(years, "2024-2025", null)
        )
        assertEquals(null, SchoolCalendarYearPolicy.select(emptyList(), null, null))
    }
}
