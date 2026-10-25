package com.ahu.ahutong.data.update

import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class ApkDownloaderContractTest {

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
