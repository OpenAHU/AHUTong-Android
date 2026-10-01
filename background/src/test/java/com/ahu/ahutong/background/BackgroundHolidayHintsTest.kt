package com.ahu.ahutong.background

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.schedule.ScheduleHoliday
import com.ahu.ahutong.data.schedule.ScheduleHolidaySource
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BackgroundHolidayHintsTest {
    @Test
    fun `actual start date is used even when reminder starts on previous date`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val courseStart = LocalDate.parse("2027-01-01").atTime(0, 5).atZone(zone).toInstant().toEpochMilli()

        assertEquals(LocalDate.parse("2027-01-01"), backgroundCourseDate(courseStart, zone))
        assertNull(backgroundCourseDate(null, zone))
        assertNull(backgroundCourseDate(-1L, zone))
    }

    @Test
    fun `delivery resolves holiday from online result using complete date`() = runBlocking {
        val date = LocalDate.parse("2026-10-01")
        val holiday = ScheduleHoliday("国庆节", true)
        val source = FakeSource(AhuResult.Success(mapOf(date to holiday)))

        assertEquals(holiday, loadBackgroundHoliday(source, date))
        assertEquals(setOf(2026), source.requestedYears)
        assertEquals(false, source.requestedRefresh)
        assertNull(loadBackgroundHoliday(source, date.withYear(2027)))
    }

    @Test
    fun `december lookup also asks for next year and widget refresh is explicit`() = runBlocking {
        val source = FakeSource(AhuResult.Success(emptyMap()))

        assertNull(loadBackgroundHoliday(source, LocalDate.parse("2026-12-31"), refresh = true))
        assertEquals(setOf(2026, 2027), source.requestedYears)
        assertEquals(true, source.requestedRefresh)
    }

    @Test
    fun `failed online lookup does not fall back to previously published state`() = runBlocking {
        val date = LocalDate.parse("2026-10-01")
        val source = FakeSource(AhuResult.Failure(AhuError.Network)).apply {
            holidays.value = mapOf(date to ScheduleHoliday("国庆节", true))
        }

        assertNull(loadBackgroundHoliday(source, date))
    }

    @Test
    fun `revocation while a lookup is finishing suppresses the returned old annotation`() = runBlocking {
        val date = LocalDate.parse("2026-10-01")
        val holiday = ScheduleHoliday("国庆节", true)
        val source = object : ScheduleHolidaySource {
            override val holidays = MutableStateFlow(mapOf(date to holiday))
            override suspend fun load(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> {
                holidays.value = emptyMap()
                return AhuResult.Success(mapOf(date to holiday))
            }
        }

        assertNull(loadBackgroundHoliday(source, date))
    }

    @Test
    fun `slow calendar cannot indefinitely delay a course reminder`() = runBlocking {
        val source = FakeSource(AhuResult.Success(emptyMap()), hang = true)

        assertNull(loadBackgroundHoliday(source, LocalDate.parse("2026-10-01")))
    }

    @Test
    fun `two requested years share one total deadline rather than separate budgets`() = runBlocking {
        var firstYearFinished = false
        var secondYearFinished = false
        val source = object : ScheduleHolidaySource {
            override val holidays = MutableStateFlow(emptyMap<LocalDate, ScheduleHoliday>())
            override suspend fun load(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> {
                assertEquals(setOf(2026, 2027), years)
                delay(1_200L)
                firstYearFinished = true
                delay(1_200L)
                secondYearFinished = true
                return AhuResult.Success(emptyMap())
            }
        }

        assertNull(loadBackgroundHoliday(source, LocalDate.parse("2026-12-31")))
        assertTrue(firstYearFinished)
        assertFalse(secondYearFinished)
    }

    @Test
    fun `external cancellation is propagated`(): Unit = runBlocking {
        val source = object : ScheduleHolidaySource {
            override val holidays = MutableStateFlow(emptyMap<LocalDate, ScheduleHoliday>())
            override suspend fun load(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> {
                throw CancellationException("cancelled")
            }
        }

        assertFailsWith<CancellationException> {
            loadBackgroundHoliday(source, LocalDate.parse("2026-10-01"))
        }
    }

    private class FakeSource(
        val result: AhuResult<Map<LocalDate, ScheduleHoliday>>,
        val hang: Boolean = false
    ) : ScheduleHolidaySource {
        override val holidays = MutableStateFlow(emptyMap<LocalDate, ScheduleHoliday>())
        var requestedYears: Set<Int>? = null
        var requestedRefresh: Boolean? = null

        override suspend fun load(years: Set<Int>, refresh: Boolean): AhuResult<Map<LocalDate, ScheduleHoliday>> {
            requestedYears = years
            requestedRefresh = refresh
            if (hang) delay(Long.MAX_VALUE)
            result.valueOrNull()?.let { holidays.value = it }
            return result
        }
    }
}
