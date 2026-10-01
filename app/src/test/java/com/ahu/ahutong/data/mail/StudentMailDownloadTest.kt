package com.ahu.ahutong.data.mail

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StudentMailDownloadTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().build()
    }

    @After fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    private fun downloader(guard: () -> Unit = {}) = StudentMailDownload(
        client, "synthetic-session", server.url("/"), guard
    )

    private fun attachment(body: String) = MockResponse().setBody(body)
        .setHeader("Content-Disposition", "attachment; filename*=UTF-8''fixture.txt")
        .setHeader("Content-Type", "application/octet-stream")

    @Test fun streamsCapturedDownloadRequestAndReportsActualBytes() = runBlocking {
        server.enqueue(attachment("fixture bytes"))
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Pair<Long, Long?>>()
        val count = downloader().download("synthetic-message+part", "3", output) { bytes, total ->
            progress.add(bytes to total)
        }
        assertEquals("fixture bytes", output.toString("UTF-8"))
        assertEquals(13L, count)
        assertEquals(0L to 13L, progress.first())
        assertEquals(13L to 13L, progress.last())
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("GET", request.method)
        assertEquals("/js6/s", request.requestUrl!!.encodedPath)
        assertEquals("mbox:getMessageData", request.requestUrl!!.queryParameter("func"))
        assertEquals("download", request.requestUrl!!.queryParameter("mode"))
        assertEquals("user_click", request.requestUrl!!.queryParameter("trigger_type"))
        assertEquals("synthetic-message+part", request.requestUrl!!.queryParameter("mid"))
        assertEquals("3", request.requestUrl!!.queryParameter("part"))
        assertEquals("synthetic-session", request.requestUrl!!.queryParameter("sid"))
        assertEquals("mail.stu.ahu.edu.cn", request.requestUrl!!.queryParameter("_host"))
        assertEquals("sirius-web", request.requestUrl!!.queryParameter("_appName"))
    }

    @Test fun supportsUnknownSizeWithoutBufferingWholeFile() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody("streamed fixture", 3)
            .setHeader("Content-Disposition", "attachment; filename=fixture.txt"))
        val progress = mutableListOf<Pair<Long, Long?>>()
        val output = ByteArrayOutputStream()
        downloader().download("message", "3", output) { bytes, total -> progress.add(bytes to total) }
        assertEquals("streamed fixture", output.toString("UTF-8"))
        assertTrue(progress.all { it.second == null })
    }

    @Test fun rejectsLoginRedirectWithoutFollowingIt() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/login")))
        val output = ByteArrayOutputStream()
        val error = runCatching { downloader().download("message", "3", output) }.exceptionOrNull()
        assertTrue(error is MailSessionExpired)
        assertEquals(0, output.size())
        assertEquals(1, server.requestCount)
    }

    @Test fun rejectsSuccessfulHttpLoginHtmlBeforeSavingBytes() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html")
            .setBody("<form action='login'><input type='password' value='private-value'></form>"))
        val output = ByteArrayOutputStream()
        val error = runCatching { downloader().download("message", "3", output) }.exceptionOrNull()
        assertTrue(error is MailSessionExpired)
        assertEquals(0, output.size())
        assertFalse(error!!.message.orEmpty().contains("private-value"))
    }

    @Test fun rejectsSessionJsonAndOtherErrorsBeforeSavingBytes() = runBlocking {
        for (code in listOf("S_INVALID_SID", "S_INVALID_SESSION", "S_ERROR")) {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"code":"$code","message":"private-value"}"""))
            val output = ByteArrayOutputStream()
            val error = runCatching { downloader().download("message", "3", output) }.exceptionOrNull()
            if (code == "S_ERROR") assertTrue(error is MailServiceFailure)
            else assertTrue(error is MailSessionExpired)
            assertEquals(0, output.size())
            assertFalse(error!!.message.orEmpty().contains("private-value"))
        }
    }

    @Test fun savesLegitimateHtmlAttachmentEvenWhenItContainsAForm() = runBlocking {
        val html = "<form action='login'><input type='password'></form>"
        server.enqueue(attachment(html).setHeader("Content-Type", "text/html"))
        val output = ByteArrayOutputStream()
        downloader().download("message", "3", output)
        assertEquals(html, output.toString("UTF-8"))
    }

    @Test fun rejectsOldIdentityBeforeRequestLeavesDevice() = runBlocking {
        val error = runCatching {
            downloader { throw MailSessionExpired() }.download("message", "3", ByteArrayOutputStream())
        }.exceptionOrNull()
        assertTrue(error is MailSessionExpired)
        assertEquals(0, server.requestCount)
    }

    @Test fun checksIdentityBeforeSavingResponseBytes() = runBlocking {
        server.enqueue(attachment("fixture"))
        var checks = 0
        val output = ByteArrayOutputStream()
        val error = runCatching {
            downloader { if (++checks > 1) throw MailSessionExpired() }.download("message", "3", output)
        }.exceptionOrNull()
        assertTrue(error is MailSessionExpired)
        assertEquals(0, output.size())
    }

    @Test fun identityChangeDuringTransferFailsBeforeCompletion() = runBlocking {
        server.enqueue(attachment("x".repeat(100_000)))
        var current = true
        val output = ByteArrayOutputStream()
        val error = runCatching {
            downloader { if (!current) throw MailSessionExpired() }.download("message", "3", output) { bytes, _ ->
                if (bytes > 0) current = false
            }
        }.exceptionOrNull()
        assertTrue(error is MailSessionExpired)
        assertTrue(output.size() in 1 until 100_000)
    }

    @Test fun cancellationClosesSocketWhileBodyIsStreaming() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(100_000)))
            .setHeader("Content-Disposition", "attachment; filename=fixture.bin")
            .throttleBody(1024, 1, TimeUnit.SECONDS))
        val output = ByteArrayOutputStream()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            downloader().download("message", "3", output)
        }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        withTimeout(2_000) { while (output.size() == 0) delay(10) }
        pending.cancelAndJoin()
        withTimeout(2_000) { while (client.dispatcher.runningCallsCount() != 0) delay(10) }
        assertTrue(output.size() < 100_000)
        assertTrue(pending.isCancelled)
    }

    @Test fun doesNotRetryOrAcceptPartialResponse() = runBlocking {
        server.enqueue(attachment("x".repeat(100_000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        server.enqueue(attachment("should not be requested"))
        val output = ByteArrayOutputStream()
        val error = runCatching { downloader().download("message", "3", output) }.exceptionOrNull()
        assertTrue(error is MailServiceFailure)
        assertTrue(output.size() < 100_000)
        assertEquals(1, server.requestCount)
    }

    @Test fun rejectsUntrustedHostAndInvalidPartWithoutRequest() = runBlocking {
        val fakeSession = MailSession("session", "mail.stu.ahu.edu.cn.evil.test", client,
            "synthetic-account", { true }, MailMemoryCookieJar())
        assertTrue(runCatching { StudentMailDownload(fakeSession) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            downloader().download("message", "../login", ByteArrayOutputStream())
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }
}
