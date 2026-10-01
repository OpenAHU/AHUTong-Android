package com.ahu.ahutong.data.calendar

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class HolidayCalendarHttpTest {
    @Test
    fun `complete body is read before success`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"days\":[]}"))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/2026.json")).build())
            assertEquals("{\"days\":[]}", awaitHolidayCalendarJson(call, 128 * 1024))
        }
    }

    @Test
    fun `response limit also rejects chunked bodies`(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setChunkedBody("a".repeat(1025), 32))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/2026.json")).build())
            assertFailsWith<IllegalArgumentException> { awaitHolidayCalendarJson(call, 1024) }
        }
    }

    @Test
    fun `background deadline cancels a stalled body after headers arrive`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}").setBodyDelay(5, TimeUnit.SECONDS))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/2026.json")).build())
            val startedAt = System.nanoTime()
            assertNull(withTimeoutOrNull(300) { awaitHolidayCalendarJson(call, 128 * 1024) })
            val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            assertTrue(call.isCanceled())
            assertTrue(elapsedMillis < 2_000, "body read exceeded cancellation budget: $elapsedMillis ms")
        }
    }
}
