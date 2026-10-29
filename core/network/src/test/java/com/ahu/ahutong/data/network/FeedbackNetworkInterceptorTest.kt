package com.ahu.ahutong.data.network

import com.ahu.ahutong.core.common.diagnostics.DiagnosticCategory
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.GzipSink
import okio.Source
import okio.Timeout
import okio.buffer

class FeedbackNetworkInterceptorTest {
    @BeforeTest fun reset() = FeedbackDiagnostics.clear()
    @AfterTest fun clear() = FeedbackDiagnostics.clear()

    @Test fun redirectsCaptureCookieJarHeadersAndDoNotReplayOneShotBodies() {
        val server = MockWebServer()
        val writes = AtomicInteger()
        val cookies = mutableListOf<Cookie>()
        val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) { cookies += newCookies }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies
        }).addNetworkInterceptor(FeedbackNetworkInterceptor()).build()
        try {
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/done")
                .setHeader("Set-Cookie", "session=synthetic-session; Path=/").setBody(""))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"result\":\"good\",\"password\":\"response-password\"}"))
            val payload = "account=synthetic-user&password=request-password&rsa=encrypted-password"
            val body = object : RequestBody() {
                override fun contentType() = "application/x-www-form-urlencoded".toMediaType()
                override fun contentLength() = -1L
                override fun isOneShot() = true
                override fun writeTo(sink: BufferedSink) { writes.incrementAndGet(); sink.writeUtf8(payload) }
            }
            client.newCall(Request.Builder().url(server.url("/login?token=synthetic-token"))
                .header("Authorization", "Bearer synthetic-bearer").post(body).build())
                .execute().use { response ->
                    assertEquals("{\"result\":\"good\",\"password\":\"response-password\"}", response.body.string())
                }
            assertEquals(payload, server.takeRequest().body.readUtf8())
            assertEquals("session=synthetic-session", server.takeRequest().getHeader("Cookie"))
            assertEquals(1, writes.get())
            val details = FeedbackDiagnostics.snapshot(DiagnosticCategory.NETWORK)
            assertTrue(details.contains("synthetic-session"))
            assertTrue(details.contains("synthetic-bearer"))
            assertTrue(details.contains("synthetic-token"))
            assertTrue(details.contains("synthetic-user"))
            listOf("request-password", "encrypted-password", "response-password").forEach {
                assertFalse(details.contains(it), "Password reached detailed ring: $it")
            }
            val summary = FeedbackDiagnostics.snapshot(DiagnosticCategory.RUNTIME)
            assertFalse(summary.contains("synthetic-token"))
            assertFalse(summary.contains("synthetic-session"))
            assertFalse(summary.contains("synthetic-bearer"))
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.shutdown()
        }
    }

    @Test fun streamingResponseIsNotReadUntilTheConsumerReadsIt() {
        val reads = AtomicInteger()
        val body = observedBody("stream-message", reads, declaredLength = -1)
        val response = intercept(body)
        assertEquals(0, reads.get())
        assertFalse(FeedbackDiagnostics.snapshot(DiagnosticCategory.NETWORK).contains("stream-message"))
        assertEquals("stream-message", response.body.string())
        assertTrue(reads.get() > 0)
        assertTrue(FeedbackDiagnostics.snapshot(DiagnosticCategory.NETWORK).contains("stream-message"))
    }

    @Test fun closingUnreadStreamDoesNotReadItForDiagnostics() {
        val reads = AtomicInteger()
        val response = intercept(observedBody("never-read", reads, declaredLength = -1))
        response.close()
        assertEquals(0, reads.get())
        assertFalse(FeedbackDiagnostics.snapshot(DiagnosticCategory.NETWORK).contains("never-read"))
        assertTrue(FeedbackDiagnostics.snapshot(DiagnosticCategory.NETWORK).contains("未完整读取"))
    }

    @Test fun textCaptureIsBoundedWithoutTruncatingTheRealResponse() {
        val body = "password=hidden-password&note=" + "x".repeat(300 * 1024) + "tail-marker"
        val response = intercept(observedBody(body, AtomicInteger()))
        assertEquals(body, response.body.string())
        val details = FeedbackDiagnostics.snapshot(DiagnosticCategory.NETWORK)
        assertTrue(details.contains("256 KiB"))
        assertFalse(details.contains("tail-marker"))
        assertFalse(details.contains("hidden-password"))
    }

    @Test fun clearingWhileTheBodyIsInFlightRejectsItsLaterCompletion() {
        val response = intercept(observedBody("previous-account-response", AtomicInteger()))
        FeedbackDiagnostics.clear()
        assertEquals("previous-account-response", response.body.string())
        assertEquals("", FeedbackDiagnostics.snapshot(DiagnosticCategory.NETWORK))
        assertEquals("", FeedbackDiagnostics.snapshot(DiagnosticCategory.RUNTIME))
    }

    @Test fun gzipBytesArePreservedWhileTheirDecodedTextIsSanitized() {
        val bytes = Buffer().apply {
            GzipSink(this).buffer().use { it.writeUtf8("{\"password\":\"gzip-password\",\"result\":\"gzip-result\"}") }
        }.readByteArray()
        val body = object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = bytes.size.toLong()
            override fun source(): BufferedSource = Buffer().write(bytes)
        }
        val response = intercept(body, encoding = "gzip")
        assertTrue(response.body.bytes().contentEquals(bytes))
        val details = FeedbackDiagnostics.snapshot(DiagnosticCategory.NETWORK)
        assertTrue(details.contains("gzip-result"))
        assertFalse(details.contains("gzip-password"))
    }

    private fun observedBody(value: String, reads: AtomicInteger, declaredLength: Long = value.toByteArray().size.toLong()): ResponseBody {
        val buffer = Buffer().writeUtf8(value)
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long { reads.incrementAndGet(); return buffer.read(sink, byteCount) }
            override fun timeout() = Timeout.NONE
            override fun close() = Unit
        }.buffer()
        return object : ResponseBody() {
            override fun contentType() = "text/plain".toMediaType()
            override fun contentLength() = declaredLength
            override fun source() = source
        }
    }

    private fun intercept(body: ResponseBody, encoding: String? = null): Response {
        val request = Request.Builder().url("https://school.example/api").build()
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
            .message("OK").body(body).apply { encoding?.let { header("Content-Encoding", it) } }.build()
        val chain = object : Interceptor.Chain {
            override fun request() = request
            override fun proceed(request: Request) = response
            override fun connection(): Connection? = null
            override fun call(): Call = error("Unused")
            override fun connectTimeoutMillis() = 1000
            override fun readTimeoutMillis() = 1000
            override fun writeTimeoutMillis() = 1000
            override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
            override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
            override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
        }
        return FeedbackNetworkInterceptor().intercept(chain)
    }
}
