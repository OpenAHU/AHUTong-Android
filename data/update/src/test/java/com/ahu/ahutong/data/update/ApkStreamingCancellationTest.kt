package com.ahu.ahutong.data.update

import java.io.Closeable
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import retrofit2.Retrofit

class ApkStreamingCancellationTest {
    @Test
    fun `transparent gzip without Content-Length verifies decoded bytes using X-File-Size`() = runBlocking {
        val bytes = ByteArray(128 * 1024) { 42 }
        val encoded = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write(bytes) }
        }.toByteArray()
        val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val server = thread(isDaemon = true, name = "apk-gzip-server") {
            listener.accept().use { socket ->
                socket.soTimeout = 5_000
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { }
                socket.getOutputStream().use { output ->
                    output.write(("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\n" +
                        "X-File-Size: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                    output.write(encoded)
                }
            }
        }
        val calls = ApkDownloadCalls()
        val base = OkHttpClient.Builder().addInterceptor { chain ->
            val original = chain.request()
            assertEquals("openahu.muxyang.com", original.url.host)
            chain.proceed(original.newBuilder().url("http://127.0.0.1:${listener.localPort}/app.apk").build())
                .newBuilder().request(original).build()
        }.build()
        val client = createApkDownloadClient(base, calls)
        val api = Retrofit.Builder().baseUrl("http://127.0.0.1:${listener.localPort}/")
            .client(client).build().create(ApkDownloadApi::class.java)
        var bodyLength: Long? = null
        val transport = object : ApkDownloadTransport {
            override suspend fun download(url: String) = api.downloadByUrl(url).also {
                bodyLength = it.body()?.contentLength()
            }
            override fun cancelAll() { calls.cancelAll() }
        }
        val directory = Files.createTempDirectory("apk-gzip-test").toFile()
        val downloader = DefaultApkDownloader(ApkDirectory { directory }, transport, Dispatchers.IO)
        val events = mutableListOf<ApkDownloadEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) {
                downloader.events.onEach { events += it }.first {
                    it is ApkDownloadEvent.Succeeded || it is ApkDownloadEvent.Failed
                }
            }
        }
        try {
            val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            downloader.start(1, DOWNLOAD_URL, sha256)
            collector.join()
            assertEquals(-1L, bodyLength)
            val success = events.last() as ApkDownloadEvent.Succeeded
            assertEquals(sha256, ApkIntegrity.sha256Of(success.apkFile))
            assertEquals(bytes.size.toLong(), success.apkFile.length())
            val progress = events.filterIsInstance<ApkDownloadEvent.Progress>().map { it.fraction }
            assertEquals(0f, progress.first())
            assertEquals(1f, progress.last())
            assertTrue(progress.any { it != null && it > 0f && it < 1f })
            assertTrue(progress.zipWithNext().all { (a, b) -> a != null && b != null && a <= b })
        } finally {
            downloader.cancel()
            collector.cancelAndJoin()
            listener.close()
            server.join(2_000)
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `restarting mirror download interrupts a body after the Retrofit callback has returned`() = runBlocking {
        StalledApkServer().use { server ->
            val calls = ApkDownloadCalls()
            val bodyRead = CountDownLatch(1)
            val requests = java.util.concurrent.atomic.AtomicInteger()
            val base = OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    assertEquals("openahu.muxyang.com", original.url.host)
                    val mirror = requests.incrementAndGet() > 1
                    val response = chain.proceed(
                        original.newBuilder()
                            .url("http://127.0.0.1:${server.port}/${if (mirror) "mirror" else "primary"}")
                            .build()
                    )
                    val body = response.body!!
                    val source = object : ForwardingSource(body.source()) {
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            val count = super.read(sink, byteCount)
                            if (!mirror && count > 0) bodyRead.countDown()
                            return count
                        }
                    }.buffer()
                    response.newBuilder().request(original).body(object : ResponseBody() {
                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun source() = source
                    }).build()
                }.build()
            val client = createApkDownloadClient(base, calls)
            val api = Retrofit.Builder()
                .baseUrl("http://127.0.0.1:${server.port}/")
                .client(client).build().create(ApkDownloadApi::class.java)
            val transport = object : ApkDownloadTransport {
                override suspend fun download(url: String) = api.downloadByUrl(url)
                override fun cancelAll() {
                    calls.cancelAll()
                    client.dispatcher.cancelAll()
                }
            }
            val directory = Files.createTempDirectory("apk-streaming-test").toFile()
            val downloader = DefaultApkDownloader(ApkDirectory { directory }, transport, Dispatchers.IO)
            val completed = CountDownLatch(1)
            val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                downloader.events.collect { if (it is ApkDownloadEvent.Succeeded) completed.countDown() }
            }
            val sha256 = MessageDigest.getInstance("SHA-256").digest(server.bytes)
                .joinToString("") { "%02x".format(it) }
            try {
                downloader.start(1, DOWNLOAD_URL, sha256)
                assertTrue(bodyRead.await(2, TimeUnit.SECONDS), "Primary response body was not read")
                repeat(100) { if (client.dispatcher.runningCallsCount() != 0) delay(10) }
                assertEquals(0, client.dispatcher.runningCallsCount(), "Retrofit callback must have returned")

                downloader.start(1, DOWNLOAD_URL, sha256, forceRedownload = true)

                assertTrue(server.mirrorRequested.await(1, TimeUnit.SECONDS), "Restart waited for the stalled body")
                assertTrue(completed.await(2, TimeUnit.SECONDS), "Mirror APK was not verified")
                assertEquals(sha256, ApkIntegrity.sha256Of(ApkIntegrity.apkFile(directory, 1)))
            } finally {
                downloader.cancel()
                server.close()
                collector.cancelAndJoin()
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
                directory.deleteRecursively()
            }
        }
    }

    private class StalledApkServer : Closeable {
        val bytes = ByteArray(64 * 1024) { 42 }
        val mirrorRequested = CountDownLatch(1)
        private val listener = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        private val sockets = Collections.synchronizedList(mutableListOf<Socket>())
        private val release = CountDownLatch(1)
        val port: Int get() = listener.localPort

        init {
            thread(isDaemon = true, name = "apk-streaming-server") {
                try {
                    while (!listener.isClosed) {
                        val socket = listener.accept()
                        sockets.add(socket)
                        thread(isDaemon = true, name = "apk-streaming-response") {
                            socket.use {
                                val input = socket.getInputStream().bufferedReader()
                                val mirror = input.readLine().contains("/mirror ")
                                while (!input.readLine().isNullOrEmpty()) { }
                                val output = socket.getOutputStream()
                                output.write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                if (mirror) {
                                    mirrorRequested.countDown()
                                    output.write(bytes)
                                    output.flush()
                                } else {
                                    output.write(bytes, 0, 1)
                                    output.flush()
                                    release.await(5, TimeUnit.SECONDS)
                                }
                            }
                        }
                    }
                } catch (_: java.io.IOException) {
                    check(listener.isClosed)
                }
            }
        }

        override fun close() {
            release.countDown()
            listener.close()
            synchronized(sockets) { sockets.forEach { it.close() } }
        }
    }

    private companion object {
        const val DOWNLOAD_URL = "https://openahu.org/download/ahutong.apk"
    }
}
