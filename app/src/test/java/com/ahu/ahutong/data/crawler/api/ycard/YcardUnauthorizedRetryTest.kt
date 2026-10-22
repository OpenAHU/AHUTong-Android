package com.ahu.ahutong.data.crawler.api.ycard

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Test
import retrofit2.Response

class YcardUnauthorizedRetryTest {
    @Test fun `failed refresh leaves the 401 body readable by repository callers`() = runTest {
        val body = TrackingBody("campus card unavailable")
        val response = Response.error<String>(401, body)
        val result = retryYcardUnauthorized(response, refresh = { false }, retry = { error("must not retry") })

        assertSame(response, result)
        assertFalse(body.closed)
        assertEquals("campus card unavailable", result.errorBody()?.string())
        assertTrue(body.closed)
    }

    @Test fun `successful refresh closes the old response and retries exactly once`() = runTest {
        val body = TrackingBody("expired")
        val response = Response.error<String>(401, body)
        val second = Response.success("ok")
        var attempts = 0
        val result = retryYcardUnauthorized(response, refresh = { true }, retry = {
            assertTrue(body.closed)
            attempts++
            second
        })

        assertSame(second, result)
        assertEquals(1, attempts)
    }

    @Test fun `non authorization failures do not refresh or retry`() = runTest {
        val response = Response.error<String>(503, TrackingBody("offline"))
        assertSame(response, retryYcardUnauthorized(response,
            refresh = { error("must not refresh") }, retry = { error("must not retry") }))
        response.errorBody()?.close()
    }

    @Test fun `cancelled refresh closes the abandoned response and propagates cancellation`() = runTest {
        val body = TrackingBody("expired")
        var cancelled = false
        try {
            retryYcardUnauthorized(Response.error<String>(401, body),
                refresh = { throw CancellationException("cancelled") }, retry = { error("must not retry") })
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(body.closed)
    }

    private class TrackingBody(text: String) : ResponseBody() {
        var closed = false
        private val trackedSource = object : ForwardingSource(Buffer().writeUtf8(text)) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = -1L
        override fun source() = trackedSource
    }
}
