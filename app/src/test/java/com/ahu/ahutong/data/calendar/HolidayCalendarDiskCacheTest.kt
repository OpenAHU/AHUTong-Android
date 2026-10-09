package com.ahu.ahutong.data.calendar

import com.ahu.ahutong.data.schedule.ScheduleHoliday
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HolidayCalendarDiskCacheTest {
    private val days = mapOf(
        LocalDate.of(2026, 10, 1) to ScheduleHoliday("国庆节", true),
        LocalDate.of(2027, 1, 1) to ScheduleHoliday("元旦", false)
    )

    private fun withCache(block: (File, HolidayCalendarDiskCache) -> Unit) {
        val directory = Files.createTempDirectory("holiday-cache-test").toFile()
        try { block(directory, HolidayCalendarDiskCache(directory)) }
        finally { directory.deleteRecursively() }
    }

    @Test
    fun `restores dates and flags after a new cache instance is created`() = withCache { directory, cache ->
        cache.write(2026, days)
        assertEquals(days, HolidayCalendarDiskCache(directory).read(2026))
        assertNull(cache.read(2027))
    }

    @Test
    fun `corrupted mismatched oversized and interrupted files are ignored`() = withCache { directory, cache ->
        File(directory, "2026.json.tmp").writeText("unfinished")
        assertNull(cache.read(2026))
        val file = File(directory, "2026.json")
        file.writeText("{invalid")
        assertNull(cache.read(2026))
        cache.write(2026, days)
        val valid = file.readText()
        file.writeText(valid.replace("\"year\":2026", "\"year\":2027"))
        assertNull(cache.read(2026))
        file.writeText(valid.replace("\"formatVersion\":1", "\"formatVersion\":2"))
        assertNull(cache.read(2026))
        file.writeText(" ".repeat(128 * 1024 + 1))
        assertNull(cache.read(2026))
    }

    @Test
    fun `invalid replacement cannot destroy previous calendar`() = withCache { _, cache ->
        cache.write(2026, days)
        assertFailsWith<IllegalArgumentException> { cache.write(2026, emptyMap()) }
        assertFailsWith<IllegalArgumentException> {
            cache.write(2026, mapOf(LocalDate.of(2028, 5, 1) to ScheduleHoliday("劳动节", true)))
        }
        assertEquals(days, cache.read(2026))
    }
}
