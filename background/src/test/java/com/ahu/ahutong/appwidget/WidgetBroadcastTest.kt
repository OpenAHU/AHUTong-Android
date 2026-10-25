package com.ahu.ahutong.appwidget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WidgetBroadcastTest {
    @Test
    fun `alarm scheduling failure still finishes the broadcast exactly once`() = runBlocking {
        var finished = 0
        assertFailsWith<SecurityException> {
            runWidgetBroadcast(
                finish = { finished++ },
                scheduleNext = { throw SecurityException("fixture alarm rejection") }
            ) { }
        }
        assertEquals(1, finished)
    }

    @Test
    fun `failed work handoff still schedules the next update and finishes`() = runBlocking {
        val steps = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            runWidgetBroadcast(
                finish = { steps += "finish" },
                scheduleNext = { steps += "schedule" }
            ) {
                steps += "enqueue"
                throw IllegalStateException("fixture unavailable")
            }
        }
        assertEquals(listOf("enqueue", "schedule", "finish"), steps)
    }

    @Test
    fun `cancelled handoff releases the broadcast and propagates cancellation`() = runBlocking {
        var finished = 0
        assertFailsWith<CancellationException> {
            runWidgetBroadcast(finish = { finished++ }) {
                throw CancellationException("fixture cancellation")
            }
        }
        assertEquals(1, finished)
    }
}
