package com.ahu.ahutong.data.mail

import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StudentMailUploadTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var uploader: StudentMailUploader
    private lateinit var file: File
    private val bytes = ByteArray(100_001) { (it % 251).toByte() }

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().build()
        uploader = StudentMailUploader(client, "synthetic-sid", {}, server.url("/"), "synthetic-device")
        file = File.createTempFile("mail-upload-fixture", ".txt").apply { writeBytes(bytes) }
    }

    @After fun tearDown() {
        file.delete()
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    private fun response(actualSize: Long, id: Long = 1, composeId: String = "synthetic-compose") = MockResponse().setBody(
        """{"code":"S_OK","var":{"attachmentId":$id,"composeId":"$composeId","size":${bytes.size},"actualSize":$actualSize,"contentType":"text/plain","fileName":"fixture.txt"}}"""
    )

    @Test fun streamsCapturedPrepareAndRawByteUploadAndReportsProgress() = runBlocking {
        server.enqueue(response(0))
        server.enqueue(response(bytes.size.toLong()))
        val progress = mutableListOf<Long>()
        val attachment = uploader.upload("synthetic-compose", file, "fixture.txt", "text/plain") { sent, total ->
            assertEquals(bytes.size.toLong(), total)
            progress += sent
        }
        assertEquals("1", attachment.id)
        assertEquals(bytes.size.toLong(), attachment.size)
        val prepare = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("upload:prepare", prepare.requestUrl!!.queryParameter("func"))
        assertEquals("synthetic-sid", prepare.requestUrl!!.queryParameter("sid"))
        val json = JsonParser.parseString(prepare.body.readUtf8()).asJsonObject
        assertEquals(-1, json["attachmentId"].asInt)
        assertEquals("synthetic-compose", json["composeId"].asString)
        assertEquals("fixture.txt", json["fileName"].asString)
        assertEquals(bytes.size.toLong(), json["size"].asLong)
        assertEquals(0, json["offset"].asInt)
        val upload = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("upload:directData", upload.requestUrl!!.queryParameter("func"))
        assertEquals("1", upload.requestUrl!!.queryParameter("attachmentId"))
        assertEquals("synthetic-compose", upload.requestUrl!!.queryParameter("composeId"))
        assertEquals("0", upload.requestUrl!!.queryParameter("offset"))
        assertEquals("text/plain", upload.getHeader("Content-Type"))
        assertArrayEquals(bytes, upload.body.readByteArray())
        assertEquals(0L, progress.first())
        assertEquals(bytes.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (previous, next) -> previous <= next })
        assertTrue(progress.size > 3)
    }

    @Test fun rejectsPreparationForAnotherComposeBeforeSendingFile() = runBlocking {
        server.enqueue(response(0, composeId = "another-compose"))
        assertTrue(runCatching { uploader.upload("synthetic-compose", file, "fixture.txt", "text/plain") }
            .exceptionOrNull() is MailServiceFailure)
        assertEquals(1, server.requestCount)
    }

    @Test fun rejectsPartialUploadAndChangedAttachmentIdentity() = runBlocking {
        server.enqueue(response(0))
        server.enqueue(response(bytes.size.toLong() - 1))
        assertTrue(runCatching { uploader.upload("synthetic-compose", file, "fixture.txt", "text/plain") }
            .exceptionOrNull() is MailServiceFailure)
        server.enqueue(response(0))
        server.enqueue(response(bytes.size.toLong(), id = 2))
        assertTrue(runCatching { uploader.upload("synthetic-compose", file, "fixture.txt", "text/plain") }
            .exceptionOrNull() is MailServiceFailure)
        assertEquals(4, server.requestCount)
    }

    @Test fun uploadNeverReplaysRetryableResponse() = runBlocking {
        server.enqueue(response(0))
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(response(bytes.size.toLong()))
        assertTrue(runCatching { uploader.upload("synthetic-compose", file, "fixture.txt", "text/plain") }
            .exceptionOrNull() is MailServiceFailure)
        assertEquals(2, server.requestCount)
    }

    @Test fun uploadNeverFollowsRedirectToLogin() = runBlocking {
        server.enqueue(response(0))
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/login")))
        server.enqueue(response(bytes.size.toLong()))
        assertTrue(runCatching { uploader.upload("synthetic-compose", file, "fixture.txt", "text/plain") }
            .exceptionOrNull() is MailSessionExpired)
        assertEquals(2, server.requestCount)
    }

    @Test fun rejectsExpiredPreparationAndDoesNotLeakPrivateResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"S_INVALID_SID","message":"private-fixture-value"}"""))
        val failure = runCatching { uploader.upload("synthetic-compose", file, "fixture.txt", "text/plain") }.exceptionOrNull()
        assertTrue(failure is MailSessionExpired)
        assertFalse(failure!!.message.orEmpty().contains("private-fixture-value"))
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellationCancelsPendingUploadSocket() = runBlocking {
        server.enqueue(response(0))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            uploader.upload("synthetic-compose", file, "fixture.txt", "text/plain")
        }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        pending.cancelAndJoin()
        withTimeout(2_000) { while (client.dispatcher.runningCallsCount() != 0) delay(10) }
        assertTrue(pending.isCancelled)
    }

    @Test fun cancellationCancelsSocketWhileReadingUploadAcknowledgement() = runBlocking {
        server.enqueue(response(0))
        server.enqueue(response(bytes.size.toLong()).setBodyDelay(3, TimeUnit.SECONDS))
        val pending = async { uploader.upload("synthetic-compose", file, "fixture.txt", "text/plain") }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        delay(100)
        withTimeout(2_000) { pending.cancelAndJoin() }
        assertTrue(pending.isCancelled)
    }

    @Test fun rejectsChangedAccountBeforeAnyRequest() = runBlocking {
        val guarded = StudentMailUploader(client, "synthetic-sid", { throw MailSessionExpired() }, server.url("/"))
        assertTrue(runCatching { guarded.upload("synthetic-compose", file, "fixture.txt", "text/plain") }
            .exceptionOrNull() is MailSessionExpired)
        assertEquals(0, server.requestCount)
    }

    @Test fun streamRejectsModifiedTemporaryFileAndIsOneShot() {
        val body = MailUploadFileBody(file, "text/plain".toMediaType(), bytes.size.toLong(), {}, { _, _ -> })
        assertTrue(body.isOneShot())
        file.appendText("changed")
        assertTrue(runCatching { body.writeTo(Buffer()) }.exceptionOrNull() is java.io.IOException)
    }
}
