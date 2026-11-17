package com.ahu.ahutong.data.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AppXuexiaotongSemesterRangeProviderTest {
    private val provider = AppXuexiaotongSemesterRangeProvider()
    private val zone = ZoneId.systemDefault()

    private fun rangeFor(date: LocalDate): Pair<LocalDate, LocalDate> {
        val range = provider.rangeFor(date)
        return Instant.ofEpochMilli(range.first).atZone(zone).toLocalDate() to
            Instant.ofEpochMilli(range.last).atZone(zone).toLocalDate()
    }

    @Test
    fun `autumn covers august through january`() {
        assertEquals(
            LocalDate.of(2026, 8, 1) to LocalDate.of(2027, 1, 31),
            rangeFor(LocalDate.of(2026, 11, 17)),
        )
        assertEquals(
            LocalDate.of(2025, 8, 1) to LocalDate.of(2026, 1, 31),
            rangeFor(LocalDate.of(2026, 1, 10)),
        )
    }

    @Test
    fun `spring covers february through july`() {
        assertEquals(
            LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 7, 31),
            rangeFor(LocalDate.of(2026, 3, 1)),
        )
        assertEquals(
            LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 7, 31),
            rangeFor(LocalDate.of(2026, 7, 31)),
        )
    }
}
