package com.ahu.ahutong.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnexpectedErrorQueueTest {
    @Test
    fun `reports remain pending until the foreground host dismisses them`() {
        val queue = UnexpectedErrorQueue()
        queue.enqueue("first")
        queue.enqueue("second")
        val first = queue.reports.value.first()
        assertEquals(listOf("first", "second"), queue.reports.value.map { it.details })
        queue.dismiss(first.id)
        assertEquals(listOf("second"), queue.reports.value.map { it.details })
        queue.dismiss(first.id)
        assertEquals(1, queue.reports.value.size)
    }

    @Test
    fun `automatic error id is retained for feedback association`() {
        val queue = UnexpectedErrorQueue()
        queue.enqueue("failure", "0123456789abcdef0123456789abcdef")
        assertEquals("0123456789abcdef0123456789abcdef", queue.reports.value.single().sentryEventId)
    }

    @Test
    fun `repeated failures remain quiet after dismissal until cooldown expires`() {
        var now = 1_000L
        val queue = UnexpectedErrorQueue(nowMillis = { now })
        assertTrue(queue.enqueue("failure"))
        queue.dismiss(queue.reports.value.single().id)
        now += 59_999
        assertFalse(queue.enqueue("failure"))
        now += 1
        assertTrue(queue.enqueue("failure"))
    }

    @Test
    fun `an error storm has a bounded queue and cannot displace the visible report`() {
        val queue = UnexpectedErrorQueue()
        repeat(100) { queue.enqueue("failure $it") }
        assertEquals(listOf("failure 0", "failure 1", "failure 2"), queue.reports.value.map { it.details })
    }

    @Test
    fun `an active report cannot be duplicated even after cooldown expires`() {
        var now = 1_000L
        val queue = UnexpectedErrorQueue(nowMillis = { now })
        queue.enqueue("failure")
        now += 120_000
        assertFalse(queue.enqueue("failure"))
    }
}
