package com.ahu.ahutong.data.xuexiaotong

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkDeadlineParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun time(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `December sync resolves January deadline into the next year`() {
        assertEquals(
            time("2026-12-31T09:00") to time("2027-01-02T18:00"),
            WorkDeadlineParser.parseRange("12-31 09:00", "01-02 18:00", time("2026-12-30T12:00"), zone)
        )
    }

    @Test
    fun `January sync and recently completed range keep the previous December`() {
        listOf("2027-01-01T12:00", "2027-01-10T12:00").forEach { now ->
            assertEquals(
                time("2026-12-31T09:00") to time("2027-01-02T18:00"),
                WorkDeadlineParser.parseRange("12-31 09:00", "01-02 18:00", time(now), zone)
            )
        }
    }

    @Test
    fun `explicit server years override device year and support seconds`() {
        assertEquals(
            time("2024-12-31T09:00:30") to time("2025-01-02T18:00:45"),
            WorkDeadlineParser.parseRange("2024-12-31 09:00:30", "2025-01-02 18:00:45", time("2026-09-06T12:00"), zone)
        )
    }

    @Test
    fun `a year supplied on one endpoint anchors the other endpoint`() {
        val expected = time("2026-12-31T09:00") to time("2027-01-02T18:00")
        assertEquals(expected, WorkDeadlineParser.parseRange("12-31 09:00", "2027-01-02 18:00", zone = zone))
        assertEquals(expected, WorkDeadlineParser.parseRange("2026-12-31 09:00", "01-02 18:00", zone = zone))
    }

    @Test
    fun `same year ranges retain their reference year`() {
        assertEquals(
            time("2026-09-06T09:00") to time("2026-09-08T18:00"),
            WorkDeadlineParser.parseRange("09-06 09:00", "09-08 18:00", time("2026-09-06T12:00"), zone)
        )
    }

    @Test
    fun `yearless January range first synced in December belongs to the new year`() {
        assertEquals(
            time("2027-01-01T09:00") to time("2027-01-02T18:00"),
            WorkDeadlineParser.parseRange("01-01 09:00", "01-02 18:00", time("2026-12-30T12:00"), zone)
        )
        assertEquals(
            time("2026-12-28T09:00") to time("2026-12-30T18:00"),
            WorkDeadlineParser.parseRange("12-28 09:00", "12-30 18:00", time("2027-01-03T12:00"), zone)
        )
    }

    @Test
    fun `cached start anchors a historical range independently of the device year`() {
        assertEquals(
            time("2024-01-01T09:00") to time("2024-01-02T18:00"),
            WorkDeadlineParser.parseRange("01-01 09:00", "01-02 18:00", time("2024-01-01T09:00"), zone)
        )
    }

    @Test
    fun `invalid dates and reversed explicit ranges do not silently normalize`() {
        val now = time("2026-09-06T12:00")
        assertNull(WorkDeadlineParser.parseRange("02-30 09:00", "03-01 18:00", now, zone))
        assertNull(WorkDeadlineParser.parseRange("09-06 25:00", "09-07 18:00", now, zone))
        assertNull(WorkDeadlineParser.parseRange("2026-12-31 09:00", "2026-01-02 18:00", now, zone))
        assertNull(WorkDeadlineParser.parseRange("09-06 18:00", "09-06 09:00", now, zone))
    }
}
