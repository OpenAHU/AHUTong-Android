package com.ahu.ahutong.appwidget

import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.schedule.WidgetScheduleReadModel
import com.ahu.ahutong.data.schedule.WidgetScheduleSnapshot
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WidgetRenderDataTest {
    private val snapshot = WidgetScheduleSnapshot(emptyList(), ScheduleConfigBean())

    @Test
    fun `cold widget dependency and encrypted cache reads leave the caller thread`() = runBlocking {
        val caller = Thread.currentThread()
        var resolutions = 0
        var reads = 0
        val model = object : WidgetScheduleReadModel {
            override fun cachedSnapshot(): WidgetScheduleSnapshot {
                assertNotSame(caller, Thread.currentThread(), "Encrypted storage must not block the widget main thread")
                reads++
                return snapshot
            }
        }
        val result = loadWidgetRenderData(
            readModel = {
                assertNotSame(caller, Thread.currentThread(), "Cold dependency construction can also read storage")
                resolutions++
                model
            },
            now = { Date(0) }
        )
        assertSame(snapshot, result.schedule)
        assertEquals(1, resolutions)
        assertEquals(1, reads)
    }

    @Test
    fun `storage backed debug clock is captured off the caller thread once`() = runBlocking {
        val caller = Thread.currentThread()
        val date = Date(123_456L)
        var clockReads = 0
        val result = loadWidgetRenderData(
            readModel = { object : WidgetScheduleReadModel {
                override fun cachedSnapshot() = snapshot
            } },
            now = {
                assertNotSame(caller, Thread.currentThread(), "Reading mock time can reach Keystore")
                clockReads++
                date
            }
        )
        repeat(5) { result.currentMinutes; result.now; result.schedule }
        assertSame(date, result.now)
        assertEquals(1, clockReads)
    }

    @Test
    fun `a superseded render cannot publish after its blocking cache read finishes`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var published = false
        val render = async {
            loadWidgetRenderData(
                readModel = { object : WidgetScheduleReadModel {
                    override fun cachedSnapshot(): WidgetScheduleSnapshot {
                        started.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        return snapshot
                    }
                } },
                now = { Date(0) }
            )
            published = true
        }
        try {
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
            render.cancel()
        } finally {
            release.countDown()
        }
        render.join()
        assertEquals(false, published)
    }
}
