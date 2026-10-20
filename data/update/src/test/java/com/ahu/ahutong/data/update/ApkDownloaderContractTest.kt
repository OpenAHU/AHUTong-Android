package com.ahu.ahutong.data.update

import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Response

class ApkDownloaderContractTest {

    @Test
    fun `ESA response without Content-Length reports progress from X-File-Size on the mirror`() = runBlocking {
        val bytes = ByteArray(128 * 1024) { 42 }
        val directory = createTempDirectory("apk-esa-test").toFile()
        val urls = mutableListOf<String>()
        val transport = object : ApkDownloadTransport {
            override suspend fun download(url: String): Response<ResponseBody> {
                urls += url
                val body = object : ResponseBody() {
                    private val buffer = Buffer().write(bytes)
                    override fun contentType() = null
                    override fun contentLength() = -1L
                    override fun source() = buffer
                }
                val raw = okhttp3.Response.Builder()
                    .request(Request.Builder().url(url).build())
                    .protocol(Protocol.HTTP_2)
                    .code(200)
                    .message("OK")
                    .header("x-file-size", bytes.size.toString())
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
            downloader.start(331, DOWNLOAD_URL, sha256(bytes))
            collector.join()
            assertTrue(events.last() is ApkDownloadEvent.Succeeded)
            val progress = events.filterIsInstance<ApkDownloadEvent.Progress>().map { it.fraction }
            assertEquals(0f, progress.first())
            assertEquals(1f, progress.last())
            assertTrue(progress.any { it != null && it > 0f && it < 1f })
            assertTrue(progress.zipWithNext().all { (before, after) -> before != null && after != null && before <= after })
            assertEquals(listOf("https://openahu.muxyang.com/download/app.apk"), urls)
        } finally {
            collector.cancel()
            downloader.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `fake transport drives a verified download through the public event interface`() = runBlocking {
        val bytes = "verified apk bytes".toByteArray()
        val directory = createTempDirectory("apk-downloader-test").toFile()
        val transport = FakeApkDownloadTransport(bytes)
        val downloader = DefaultApkDownloader(
            directory = ApkDirectory { directory },
            transport = transport,
            dispatcher = Dispatchers.Unconfined
        )
        val events = mutableListOf<ApkDownloadEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            downloader.events.take(4).toList(events)
        }

        try {
            downloader.start(
                versionCode = 331,
                downloadUrl = DOWNLOAD_URL,
                sha256 = sha256(bytes)
            )
            collector.join()

            assertEquals(listOf("https://openahu.muxyang.com/download/app.apk"), transport.urls)
            assertTrue(events.first() is ApkDownloadEvent.Started)
            val success = events.last() as ApkDownloadEvent.Succeeded
            assertContentEquals(bytes, success.apkFile.readBytes())
            assertTrue(!success.alreadyLocal)
        } finally {
            collector.cancel()
            downloader.cancel()
            directory.deleteRecursively()
        }
        assertEquals(1, transport.cancelCount)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class FakeApkDownloadTransport(
        private val bytes: ByteArray
    ) : ApkDownloadTransport {
        val urls = mutableListOf<String>()
        var cancelCount = 0

        override suspend fun download(url: String): Response<okhttp3.ResponseBody> {
            urls += url
            val raw = okhttp3.Response.Builder()
                .request(Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(bytes.toResponseBody(), raw)
        }

        override fun cancelAll() {
            cancelCount += 1
        }
    }

    private companion object {
        const val DOWNLOAD_URL = "https://openahu.org/download/app.apk"
    }
}
