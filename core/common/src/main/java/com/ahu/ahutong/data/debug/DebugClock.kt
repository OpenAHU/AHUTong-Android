package com.ahu.ahutong.data.debug

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Duration
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DebugClock {
    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    private fun currentInstant(): Instant {
        val mocked = DebugTimeSourceHolder.mockedNowMillis()
        return if (mocked != null) Instant.ofEpochMilli(mocked) else Instant.now()
    }

    fun isMocked(): Boolean = DebugTimeSourceHolder.mockedNowMillis() != null

    fun nowDate(): Date = Date.from(currentInstant())

    fun nowLocalDate(): LocalDate = LocalDateTime.ofInstant(currentInstant(), zoneId).toLocalDate()

    fun nowLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(currentInstant(), zoneId)

    fun nowZonedDateTime(): ZonedDateTime = currentInstant().atZone(zoneId)

    /** A regular UI heartbeat must not sleep past the next local date boundary. */
    fun nextCalendarRefreshDelayMillis(
        now: ZonedDateTime,
        maximumMillis: Long,
        mocked: Boolean = isMocked()
    ): Long {
        require(maximumMillis > 0)
        if (mocked) return maximumMillis
        val nextDate = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return minOf(maximumMillis, Duration.between(now, nextDate).toMillis().coerceAtLeast(1L))
    }

    fun nowCalendar(locale: Locale = Locale.CHINA): Calendar {
        return Calendar.getInstance(locale).apply {
            time = nowDate()
        }
    }

    fun currentMinutes(locale: Locale = Locale.CHINA): Int {
        val calendar = nowCalendar(locale)
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }
}
