package com.ahu.ahutong.data.calendar

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.schedule.ScheduleHoliday
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkHolidayCalendarTest {
    private val date = LocalDate.of(2026, 10, 1)
    private val days = mapOf(date to ScheduleHoliday("国庆节", true))
    private val updated = mapOf(date to ScheduleHoliday("国庆节", false))

    private class MemoryCache : HolidayCalendarCache {
        val years = mutableMapOf<Int, Map<LocalDate, ScheduleHoliday>>()
        var writes = 0
        override fun read(year: Int) = years[year]
        override fun write(year: Int, days: Map<LocalDate, ScheduleHoliday>) {
            writes++
            years[year] = days
        }
    }

    @Test
    fun `restart restores cache before slow refresh and replaces changed data`() = runTest {
        val cache = MemoryCache()
        val first = NetworkHolidayCalendar({ true }, { AhuResult.Success(days) },
            cache = cache, refreshScope = backgroundScope)
        first.load(setOf(2026), false)
        val response = CompletableDeferred<AhuResult<Map<LocalDate, ScheduleHoliday>>>()
        val restarted = NetworkHolidayCalendar({ true }, { response.await() },
            cache = cache, refreshScope = backgroundScope)
        assertEquals(days, withTimeout(100) { restarted.load(setOf(2026), false) }.valueOrNull())
        runCurrent()
        assertEquals(days, restarted.holidays.value)
        // 正在刷新也不等待网络锁，组件的短查询预算不会取消共享刷新。
        assertEquals(days, withTimeout(100) { restarted.load(setOf(2026), false) }.valueOrNull())
        response.complete(AhuResult.Success(updated))
        runCurrent()
        assertEquals(updated, restarted.holidays.value)
        assertEquals(updated, cache.years[2026])
        assertEquals(2, cache.writes)
    }

    @Test
    fun `identical refresh does not emit or rewrite and manual requests are throttled`() = runTest {
        var now = 0L
        var calls = 0
        val cache = MemoryCache()
        val source = NetworkHolidayCalendar({ true }, { calls++; AhuResult.Success(days) },
            { now }, cache, backgroundScope)
        source.load(setOf(2026), false)
        val emissions = mutableListOf<Map<LocalDate, ScheduleHoliday>>()
        backgroundScope.launch { source.holidays.collect { emissions.add(it) } }
        runCurrent()
        source.load(setOf(2026), false)
        source.load(setOf(2026), true)
        runCurrent()
        assertEquals(1, calls)
        now = NetworkHolidayCalendar.MIN_REQUEST_MILLIS
        source.load(setOf(2026), true)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(1, cache.writes)
        assertEquals(listOf(days), emissions)
    }

    @Test
    fun `offline restores cache without fetching and missing year fails`() = runTest {
        var calls = 0
        val cache = MemoryCache().apply { years[2026] = days }
        val source = NetworkHolidayCalendar({ false }, { calls++; AhuResult.Success(days) },
            cache = cache, refreshScope = backgroundScope)
        assertEquals(days, source.load(setOf(2026), false).valueOrNull())
        runCurrent()
        assertTrue(source.load(setOf(2027), false).isFailure)
        assertEquals(days, source.holidays.value)
        assertEquals(0, calls)
    }

    @Test
    fun `failed or throwing refresh keeps old data and throttles retry`() = runTest {
        var now = 0L
        var calls = 0
        val cache = MemoryCache().apply { years[2026] = days }
        val source = NetworkHolidayCalendar({ true }, {
            calls++
            if (calls == 1) AhuResult.Failure(AhuError.Network) else error("network failure")
        }, { now }, cache, backgroundScope)
        source.load(setOf(2026), true)
        runCurrent()
        now++
        source.load(setOf(2026), true)
        runCurrent()
        assertEquals(1, calls)
        now = NetworkHolidayCalendar.FAILURE_RETRY_MILLIS
        source.load(setOf(2026), true)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(days, source.holidays.value)
        assertEquals(days, cache.years[2026])
        assertEquals(0, cache.writes)
    }

    @Test
    fun `expired freshness keeps markings throughout slow refresh`() = runTest {
        var now = 0L
        var calls = 0
        val source = NetworkHolidayCalendar({ true }, {
            calls++
            if (calls == 1) AhuResult.Success(days) else awaitCancellation()
        }, { now }, MemoryCache(), backgroundScope)
        source.load(setOf(2026), false)
        now = NetworkHolidayCalendar.VALID_MILLIS
        assertEquals(days, source.load(setOf(2026), false).valueOrNull())
        runCurrent()
        assertEquals(2, calls)
        assertEquals(days, source.holidays.value)
    }

    @Test
    fun `disconnect rejects late response while preserving cached markings`() = runTest {
        val cache = MemoryCache().apply { years[2026] = days }
        val response = CompletableDeferred<AhuResult<Map<LocalDate, ScheduleHoliday>>>()
        val source = NetworkHolidayCalendar({ true }, { response.await() },
            cache = cache, refreshScope = backgroundScope)
        source.load(setOf(2026), false)
        runCurrent()
        source.onNetworkLost()
        response.complete(AhuResult.Success(updated))
        runCurrent()
        assertEquals(days, source.holidays.value)
        assertEquals(days, cache.years[2026])
    }

    @Test
    fun `cold concurrent callers share request and unknown year is never invented`() = runTest {
        var calls = 0
        val response = CompletableDeferred<AhuResult<Map<LocalDate, ScheduleHoliday>>>()
        val source = NetworkHolidayCalendar({ true }, { year ->
            calls++
            if (year == 2026) response.await() else AhuResult.Failure(AhuError.Network)
        }, cache = MemoryCache(), refreshScope = backgroundScope)
        val first = async { source.load(setOf(2026), false) }
        val second = async { source.load(setOf(2026), false) }
        runCurrent()
        assertEquals(1, calls)
        response.complete(AhuResult.Success(days))
        assertEquals(days, first.await().valueOrNull())
        assertEquals(days, second.await().valueOrNull())
        source.load(setOf(2026, 2027), false)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(days, source.holidays.value)
    }

    @Test
    fun `caller timeout does not cancel first download or prevent cache population`() = runTest {
        val cache = MemoryCache()
        val response = CompletableDeferred<AhuResult<Map<LocalDate, ScheduleHoliday>>>()
        val source = NetworkHolidayCalendar({ true }, { response.await() },
            cache = cache, refreshScope = backgroundScope)
        assertEquals(null, withTimeoutOrNull(2_000) { source.load(setOf(2026), false) })
        response.complete(AhuResult.Success(days))
        runCurrent()
        assertEquals(days, source.holidays.value)
        assertEquals(days, cache.years[2026])
    }

    @Test
    fun `timeout without cache leaves no partial calendar`() = runTest {
        val cache = MemoryCache()
        val source = NetworkHolidayCalendar({ true }, { awaitCancellation() },
            cache = cache, refreshScope = backgroundScope)
        assertEquals(null, withTimeoutOrNull(2_000) { source.load(setOf(2026), false) })
        assertTrue(source.holidays.value.isEmpty())
        assertTrue(cache.years.isEmpty())
    }
}
