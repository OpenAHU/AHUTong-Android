package com.ahu.ahutong.data.xuexiaotong

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WorkStatusRefreshTest {

    private val now = 1_000_000L

    private fun work(
        id: String,
        status: String = "未交",
        endTs: Long? = now + 60_000L,
        courseId: String = "c1"
    ) = Work(
        workId = id,
        courseId = courseId,
        courseName = "高等数学",
        title = "作业$id",
        status = status,
        endTs = endTs,
        startTs = endTs?.minus(86_400_000L)
    )

    @Test fun picksTheEarliestFutureDeadline() {
        val urgent = work("2", endTs = now + 30_000L)
        val later = work("1", endTs = now + 120_000L)
        assertSame(urgent, WorkStatusRefresh.pickMostUrgent(listOf(later, urgent), now))
    }

    @Test fun ignoresDoneWorks() {
        val done = work("1", status = "已完成", endTs = now + 30_000L)
        val todo = work("2", endTs = now + 60_000L)
        assertSame(todo, WorkStatusRefresh.pickMostUrgent(listOf(done, todo), now))
    }

    @Test fun ignoresOverdueAndUndatedWorks() {
        val overdue = work("1", endTs = now - 1L)
        val undated = work("2", endTs = null)
        assertNull(WorkStatusRefresh.pickMostUrgent(listOf(overdue, undated), now))
    }

    @Test fun mergeKeepsPreciseTimesAndTakesTheNewStatus() {
        val cached = work("1", status = "未交", endTs = now + 60_000L)
        val refreshed = work("1", status = "已完成", endTs = now + 55_000L)
        val merged = WorkStatusRefresh.merge(listOf(cached), refreshed).single()
        assertEquals("已完成", merged.status)
        assertEquals(now + 55_000L, merged.endTs)
        assertEquals(cached.startTs, merged.startTs)
    }

    @Test fun mergeWithAnUnknownWorkChangesNothing() {
        val cached = listOf(work("1"))
        val other = work("9")
        assertSame(cached, WorkStatusRefresh.merge(cached, other))
    }
}
