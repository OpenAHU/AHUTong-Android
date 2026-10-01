package com.ahu.ahutong.data.calendar

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.schedule.ScheduleHoliday
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.awaitCancellation

class NetworkHolidayCalendarTest {
    private val date = LocalDate.of(2026, 10, 1)
    private val days = mapOf(date to ScheduleHoliday("国庆节", true))

    @Test
    fun `online calls share fresh memory and manual requests remain throttled`() = runTest {
        var now = 0L
        var calls = 0
        val source = NetworkHolidayCalendar({ true }, { calls++; AhuResult.Success(days) }, { now })
        source.load(setOf(2026), false)
        source.load(setOf(2026), false)
        source.load(setOf(2026), true)
        assertEquals(1, calls)
        assertEquals(days, source.holidays.value)
        now = NetworkHolidayCalendar.MIN_REQUEST_MILLIS
        source.load(setOf(2026), true)
        assertEquals(2, calls)
    }

    @Test
    fun `offline never fetches or recovers previous results`() = runTest {
        var online = false
        var calls = 0
        val source = NetworkHolidayCalendar({ online }, { calls++; AhuResult.Success(days) })
        assertTrue(source.load(setOf(2026), false).isFailure)
        assertEquals(0, calls)
        online = true
        source.load(setOf(2026), false)
        online = false
        source.invalidate()
        assertEquals(emptyMap(), source.holidays.value)
        assertTrue(source.load(setOf(2026), false).isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun `failed refresh revokes old year and throttles failures`() = runTest {
        var now = 0L
        var calls = 0
        val source = NetworkHolidayCalendar({ true }, {
            calls++
            if (calls == 1) AhuResult.Success(days) else AhuResult.Failure(AhuError.Network)
        }, { now })
        source.load(setOf(2026), false)
        now = NetworkHolidayCalendar.MIN_REQUEST_MILLIS
        assertTrue(source.load(setOf(2026), true).isFailure)
        assertEquals(emptyMap(), source.holidays.value)
        now++
        source.load(setOf(2026), true)
        assertEquals(2, calls)
    }

    @Test
    fun `expiration disables markings until another successful network request`() = runTest {
        var now = 0L
        val source = NetworkHolidayCalendar({ true }, { AhuResult.Success(days) }, { now })
        source.load(setOf(2026), false)
        now = NetworkHolidayCalendar.VALID_MILLIS
        source.expire()
        assertEquals(emptyMap(), source.holidays.value)
        source.load(setOf(2026), false)
        assertEquals(days, source.holidays.value)
    }

    @Test
    fun `late response after disconnect cannot restore markings even when connection returns`() = runTest {
        val response = CompletableDeferred<AhuResult<Map<LocalDate, ScheduleHoliday>>>()
        val source = NetworkHolidayCalendar({ true }, { response.await() })
        val pending = async { source.load(setOf(2026), false) }
        runCurrent()
        source.invalidate()
        response.complete(AhuResult.Success(days))
        assertTrue(pending.await().isFailure)
        assertEquals(emptyMap(), source.holidays.value)
    }

    @Test
    fun `timeout during revalidation does not restore previous calendar`() = runTest {
        var now = 0L
        var calls = 0
        val source = NetworkHolidayCalendar({ true }, {
            calls++
            if (calls == 1) AhuResult.Success(days) else awaitCancellation()
        }, { now })
        source.load(setOf(2026), false)
        now = NetworkHolidayCalendar.MIN_REQUEST_MILLIS
        assertEquals(null, withTimeoutOrNull(2_000) { source.load(setOf(2026), true) })
        assertEquals(emptyMap(), source.holidays.value)
    }

    @Test
    fun `unknown year stays unmarked while another year succeeds`() = runTest {
        val source = NetworkHolidayCalendar({ true }, { year ->
            if (year == 2026) AhuResult.Success(days) else AhuResult.Failure(AhuError.Network)
        })
        source.load(setOf(2026, 2027), false)
        assertEquals(days, source.holidays.value)
        assertTrue(source.holidays.value.keys.none { it.year == 2027 })
    }
}
