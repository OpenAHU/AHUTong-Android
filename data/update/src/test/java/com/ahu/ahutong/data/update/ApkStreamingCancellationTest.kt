package com.ahu.ahutong.data.update

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import retrofit2.Retrofit

class ApkStreamingCancellationTest {
    @Test
    fun `switching mirror interrupts a body after the Retrofit callback has returned`() = runBlocking {
        StalledApkServer().use { server ->
            val calls = ApkDownloadCalls()
            val bodyRead = CountDownLatch(1)
            val base = OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val mirror = original.url.host != "openahu.org"
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

                downloader.switchToMirror(1, DOWNLOAD_URL, sha256)

                assertTrue(server.mirrorRequested.await(1, TimeUnit.SECONDS), "Switch waited for the stalled primary body")
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
