package com.ahu.ahutong.data.xuexiaotong

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncThrottleTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun time(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `course end date comes from the period line at end of day`() {
        val html = """<p>开课时间：2026-03-04～2028-03-04</p>"""
        assertEquals(time("2028-03-04T23:59:59.999"), SyncThrottle.parseCourseEndTs(html, zone))
    }

    @Test
    fun `open ended period yields unknown end`() {
        assertNull(SyncThrottle.parseCourseEndTs("""<p>开课时间：2026-02-25～</p>""", zone))
    }

    @Test
    fun `missing period line yields unknown end`() {
        assertNull(SyncThrottle.parseCourseEndTs("""<p class="line2 color3" title="谌正艮">谌正艮</p>""", zone))
    }

    @Test
    fun `invalid end date yields unknown end`() {
        assertNull(SyncThrottle.parseCourseEndTs("""<p>开课时间：2026-02-25～2026-13-40</p>""", zone))
    }

    @Test
    fun `half width tilde is accepted`() {
        val html = """<p>开课时间：2026-03-04~2028-03-04</p>"""
        assertEquals(time("2028-03-04T23:59:59.999"), SyncThrottle.parseCourseEndTs(html, zone))
    }

    @Test
    fun `course period exposes both start and end dates`() {
        val html = """<p>开课时间：2026-09-07～2027-01-15</p>"""
        val (start, end) = SyncThrottle.parseCoursePeriod(html, zone)
        assertEquals(time("2026-09-07T00:00"), start)
        assertEquals(time("2027-01-15T23:59:59.999"), end)
    }

    @Test
    fun `course semester follows its start date only`() {
        val semester = time("2026-08-01T00:00")..time("2027-01-31T23:59:59")
        val inside = Course(startTs = time("2026-09-07T00:00"), endTs = time("2027-01-15T00:00"))
        val before = Course(startTs = time("2026-03-01T00:00"), endTs = time("2027-01-15T00:00"))
        val missingDates = Course()
        assertTrue(with(SyncThrottle) { inside.startsInSemester(semester) })
        assertFalse(with(SyncThrottle) { before.startsInSemester(semester) })
        assertTrue(with(SyncThrottle) { missingDates.startsInSemester(semester) })
    }

    @Test
    fun `lite sync skips finished works but full sync refetches them`() {
        val done = Work(workId = "w1", status = "已完成", startTs = 100L, endTs = 200L)
        assertFalse(SyncThrottle.needsDeadlineFetch(done, full = false))
        assertTrue(SyncThrottle.needsDeadlineFetch(done, full = true))
    }

    @Test
    fun `unfinished work is always refetched so deadline edits are visible`() {
        val undone = Work(workId = "w1", status = "未交", startTs = 100L, endTs = 200L)
        assertTrue(SyncThrottle.needsDeadlineFetch(undone, full = false))
        assertTrue(SyncThrottle.needsDeadlineFetch(undone, full = true))
    }

    @Test
    fun `course ended beyond grace period is ended`() {
        val now = time("2026-09-24T12:00")
        assertTrue(Course(endTs = time("2026-09-20T23:59:59")).isEnded(now))
        assertTrue(Course(endTs = time("2026-01-11T23:59:59")).isEnded(now))
    }

    @Test
    fun `course within grace period or without end is not ended`() {
        val now = time("2026-09-24T12:00")
        assertFalse(Course(endTs = time("2026-09-22T23:59:59")).isEnded(now))
        assertFalse(Course(endTs = time("2027-02-01T23:59:59")).isEnded(now))
        assertFalse(Course(endTs = null).isEnded(now))
    }
}