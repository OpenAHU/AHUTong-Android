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
    fun `done work never fetches deadline`() {
        val cached = Work(workId = "w1", status = "已完成", startTs = 100L, endTs = 200L)
        assertFalse(SyncThrottle.needsDeadlineFetch(cached, cached))
        assertFalse(SyncThrottle.needsDeadlineFetch(Work(workId = "w1", status = "待批阅"), null))
    }

    @Test
    fun `undone work already on calendar skips deadline fetch`() {
        val cached = Work(workId = "w1", status = "未交", startTs = 100L, endTs = 200L)
        assertFalse(SyncThrottle.needsDeadlineFetch(Work(workId = "w1", status = "未交"), cached))
    }

    @Test
    fun `undone work without cached deadline fetches deadline`() {
        assertTrue(SyncThrottle.needsDeadlineFetch(Work(workId = "w1", status = "未交"), null))
        assertTrue(SyncThrottle.needsDeadlineFetch(Work(workId = "w1", status = "未交"), Work(workId = "w1", status = "未交")))
        assertTrue(SyncThrottle.needsDeadlineFetch(Work(workId = "w1", status = "未交"), Work(workId = "w1", status = "未交", startTs = 100L)))
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