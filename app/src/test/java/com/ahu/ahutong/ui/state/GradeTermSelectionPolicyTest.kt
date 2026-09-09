package com.ahu.ahutong.ui.state

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class GradeTermSelectionPolicyTest {
    @Test
    fun `current semester follows the academic year`() {
        assertEquals(
            GradeTermKey("2025-2026", "1"),
            GradeTermSelectionPolicy.currentForDate(LocalDate.of(2026, 1, 15))
        )
        assertEquals(
            GradeTermKey("2025-2026", "2"),
            GradeTermSelectionPolicy.currentForDate(LocalDate.of(2026, 3, 1))
        )
        assertEquals(
            GradeTermKey("2026-2027", "1"),
            GradeTermSelectionPolicy.currentForDate(LocalDate.of(2026, 9, 1))
        )
    }

    @Test
    fun `manual refresh preserves an available selected semester`() {
        val previous = GradeTermKey("2024-2025", "2")
        assertEquals(
            previous,
            GradeTermSelectionPolicy.choose(
                current = GradeTermKey("2025-2026", "2"),
                cached = GradeTermKey("2025-2026", "2"),
                previous = previous,
                preservePrevious = true,
                available = listOf(previous, GradeTermKey("2025-2026", "2"))
            )
        )
    }

    @Test
    fun `screen entry prefers a matching cached current semester with grades`() {
        val cached = GradeTermKey("2025-2026", "1")
        assertEquals(
            cached,
            GradeTermSelectionPolicy.choose(
                current = GradeTermKey("2025-2026", "2"),
                cached = cached,
                previous = GradeTermKey("2024-2025", "2"),
                preservePrevious = false,
                available = listOf(cached, GradeTermKey("2024-2025", "2"))
            )
        )
    }

    @Test
    fun `screen entry falls back to latest semester containing grades`() {
        val latestAvailable = GradeTermKey("2025-2026", "2")
        assertEquals(
            latestAvailable,
            GradeTermSelectionPolicy.choose(
                current = GradeTermKey("2026-2027", "1"),
                cached = GradeTermKey("2026-2027", "1"),
                previous = null,
                preservePrevious = false,
                available = listOf(
                    GradeTermKey("2024-2025", "2"),
                    latestAvailable,
                    GradeTermKey("2025-2026", "1")
                )
            )
        )
    }

    @Test
    fun `stale cached academic year falls back to calendar current semester`() {
        val current = GradeTermKey("2025-2026", "2")
        assertEquals(
            current,
            GradeTermSelectionPolicy.choose(
                current = current,
                cached = GradeTermKey("2024-2025", "2"),
                previous = null,
                preservePrevious = false,
                available = emptyList()
            )
        )
    }
}
