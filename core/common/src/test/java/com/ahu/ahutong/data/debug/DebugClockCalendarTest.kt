package com.ahu.ahutong.data.debug

import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugClockCalendarTest {
    @Test fun refreshRespectsTheLocalDateBoundaryWithoutSpinning() {
        val cases = listOf(
            "2026-10-28T23:59:59.900+08:00[Asia/Shanghai]" to 100L,
            "2026-10-29T12:00:00+08:00[Asia/Shanghai]" to 30_000L,
            "2026-10-29T00:00:00+08:00[Asia/Shanghai]" to 30_000L,
            "2026-10-28T23:59:59.999999+08:00[Asia/Shanghai]" to 1L,
            "2026-10-28T23:59:59.800-04:00[America/New_York]" to 200L
        )
        for ((time, expected) in cases) {
            val now = ZonedDateTime.parse(time)
            assertEquals(expected, DebugClock.nextCalendarRefreshDelayMillis(now, 30_000L), time)
            assertEquals(30_000L, DebugClock.nextCalendarRefreshDelayMillis(now, 30_000L, mocked = true))
        }
    }
}
