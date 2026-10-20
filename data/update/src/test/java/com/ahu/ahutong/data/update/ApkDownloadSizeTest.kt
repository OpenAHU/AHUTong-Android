package com.ahu.ahutong.data.update

import com.ahu.ahutong.data.server.ApkUpdatePolicy
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Response

class ApkDownloadSizeTest {
    private val bytes = ByteArray(128 * 1024) { 42 }

    @Test
    fun `file size takes priority over encoded content length`() = runBlocking {
        val events = download(bytes.size.toString(), bodyLength = 123)
        assertTrue(events.last() is ApkDownloadEvent.Succeeded)
        assertKnownProgress(events)
    }

    @Test
    fun `missing custom header falls back to known body length`() = runBlocking {
        val events = download(null, bodyLength = bytes.size.toLong())
        assertTrue(events.last() is ApkDownloadEvent.Succeeded)
        assertKnownProgress(events)
    }

    @Test
    fun `unknown sizes still allow a digest verified download`() = runBlocking {
        val events = download(null)
        assertTrue(events.last() is ApkDownloadEvent.Succeeded)
        assertEquals(listOf(null), progress(events))
    }

    @Test
    fun `invalid file sizes fail instead of manufacturing progress`() = runBlocking {
        listOf("0", "-1", "garbage", "1.5", "99999999999999999999999").forEach { value ->
            val events = download(value)
            assertTrue(events.last() is ApkDownloadEvent.Failed, value)
            assertTrue(progress(events).isEmpty(), value)
        }
    }

    @Test
    fun `oversized file size is rejected before body progress`() = runBlocking {
        val events = download((ApkUpdatePolicy.MAX_APK_BYTES + 1).toString())
        assertTrue(events.last() is ApkDownloadEvent.Failed)
        assertTrue(progress(events).isEmpty())
    }

    @Test
    fun `short or overlong response is rejected even if its digest matches`() = runBlocking {
        listOf(bytes.size - 1, bytes.size + 1).forEach { declared ->
            val events = download(declared.toString())
            assertTrue(events.last() is ApkDownloadEvent.Failed)
            assertFalse(events.any { it is ApkDownloadEvent.Succeeded })
        }
    }

    @Test
    fun `matching size never replaces digest verification`() = runBlocking {
        val events = download(bytes.size.toString(), expectedSha256 = "0".repeat(64))
        assertTrue(events.last() is ApkDownloadEvent.Failed)
        assertFalse(events.any { it is ApkDownloadEvent.Succeeded })
    }

    private fun progress(events: List<ApkDownloadEvent>) =
        events.filterIsInstance<ApkDownloadEvent.Progress>().map { it.fraction }

    private fun assertKnownProgress(events: List<ApkDownloadEvent>) {
        val progress = progress(events)
        assertEquals(0f, progress.first())
        assertEquals(1f, progress.last())
        assertTrue(progress.any { it != null && it > 0f && it < 1f })
        assertTrue(progress.zipWithNext().all { (before, after) -> before != null && after != null && before <= after })
    }

    private suspend fun download(
        fileSize: String?,
        bodyLength: Long = -1,
        expectedSha256: String = sha256(bytes)
    ): List<ApkDownloadEvent> = kotlinx.coroutines.coroutineScope {
        val directory = createTempDirectory("apk-size-test").toFile()
        var bodyClosed = false
        val body = object : ResponseBody() {
            private val data = Buffer().write(bytes)
            override fun contentType() = null
            override fun contentLength() = bodyLength
            override fun source() = data
            override fun close() {
                bodyClosed = true
                super.close()
            }
        }
        val transport = object : ApkDownloadTransport {
            override suspend fun download(url: String): Response<ResponseBody> {
                assertEquals("openahu.muxyang.com", Request.Builder().url(url).build().url.host)
                val raw = okhttp3.Response.Builder()
                    .request(Request.Builder().url(url).build())
                    .protocol(Protocol.HTTP_2).code(200).message("OK")
                    .apply { fileSize?.let { header("X-File-Size", it) } }
                    .build()
                return Response.success(body, raw)
            }
            override fun cancelAll() = Unit
        }
        val downloader = DefaultApkDownloader(ApkDirectory { directory }, transport, Dispatchers.Unconfined)
        val events = mutableListOf<ApkDownloadEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) {
                downloader.events.onEach { events += it }.first {
                    it is ApkDownloadEvent.Succeeded || it is ApkDownloadEvent.Failed
                }
            }
        }
        try {
            downloader.start(1, "https://openahu.org/download/app.apk", expectedSha256)
            collector.join()
            assertTrue(bodyClosed, "Response body must close on success and all failure paths")
            if (events.last() is ApkDownloadEvent.Failed) {
                assertFalse(ApkIntegrity.apkFile(directory, 1).exists())
            }
            events
        } finally {
            collector.cancel()
            downloader.cancel()
            directory.deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
