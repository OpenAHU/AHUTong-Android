package com.ahu.ahutong.data.repository

import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class RepositorySourceTransportTest {
    private val sources = listOf(
        RepositoryAccelerationSource("jsdelivr", "jsDelivr", "", useJsDelivr = true),
        RepositoryAccelerationSource("ghfast", "GHFast", "", proxyPrefix = "https://ghfast.top/"),
        RepositoryAccelerationSource("gh-proxy", "gh-proxy", "", proxyPrefix = "https://gh-proxy.com/"),
        RepositoryAccelerationSource("direct", "GitHub 直连", ""),
        RepositoryAccelerationSource(REPOSITORY_FALLBACK_SOURCE_ID, "备用源", "", isFallback = true)
    )
    private val servers = listOf(
        StorageServer("de", "https://de-ahutong-storage.muxyang.com"),
        StorageServer("hk", "https://hk-ahutong-storage.muxyang.com")
    )

    @Test
    fun `force parameter preserves the encoded path query and fragment and replaces old force`() {
        val result = forceStorageDownloadUrl(
            "https://de-ahutong-storage.muxyang.com/api/download?id=a%2Bb%2Fc&name=%E8%B5%84%E6%96%99&f=0#part"
        )
        assertEquals(
            "https://de-ahutong-storage.muxyang.com/api/download?id=a%2Bb%2Fc&name=%E8%B5%84%E6%96%99&f=1#part",
            result
        )
        assertEquals(
            "https://de-ahutong-storage.muxyang.com/api/download?id=a%2Bb%2Fc&f=1",
            storageDownloadUrl(servers.first(), "a+b/c", force = true)
        )
        assertFailsWith<IllegalArgumentException> {
            forceStorageDownloadUrl("https://gh-proxy.com/https://raw.githubusercontent.com/a/b/main/c.pdf")
        }
    }

    @Test
    fun `probe URLs are pinned and use the same four source transformations as downloads`() {
        val urls = sources.filterNot { it.isFallback }.map(::repositoryProbeUrl)
        assertEquals(4, urls.distinct().size)
        assertTrue(urls[0].contains("SpeedTest@$REPOSITORY_PROBE_COMMIT/1MiB.bin"))
        assertTrue(urls[1].startsWith("https://ghfast.top/https://raw.githubusercontent.com/"))
        assertTrue(urls.all { it.contains(REPOSITORY_PROBE_COMMIT) && it.endsWith("/1MiB.bin") })
        assertTrue(urls.none { it.contains("moeyy") || it.contains("f=1") })
        assertFailsWith<IllegalStateException> { repositoryProbeUrl(sources.last()) }
    }

    @Test
    fun `only four explicit failures enable fallback and a recovered source restores preference`() {
        val unavailable = sources.filterNot { it.isFallback }.associate { it.id to RepositorySourceHealth.UNAVAILABLE }
        assertFalse(areRepositoryPrimarySourcesUnavailable(sources, emptyMap()))
        assertEquals("jsdelivr", resolveRepositoryAccelerationSourceId(REPOSITORY_FALLBACK_SOURCE_ID, sources, emptyMap()))
        assertEquals("jsdelivr", resolveRepositoryAccelerationSourceId("moeyy", sources, unavailable - "direct"))
        assertEquals(REPOSITORY_FALLBACK_SOURCE_ID, resolveRepositoryAccelerationSourceId("ghfast", sources, unavailable))
        val recovered = unavailable + ("direct" to RepositorySourceHealth.AVAILABLE)
        assertEquals("ghfast", resolveRepositoryAccelerationSourceId("ghfast", sources, recovered))
        assertFalse(areRepositoryPrimarySourcesUnavailable(sources, unavailable + ("direct" to RepositorySourceHealth.TESTING)))
        assertFalse(areRepositoryPrimarySourcesUnavailable(emptyList(), emptyMap()))
    }

    @Test
    fun `probe requires the full one MiB and matching content even with HTTP 200`() = runBlocking {
        val bytes = ByteArray(REPOSITORY_PROBE_BYTES.toInt()) { (it % 251).toByte() }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        var responseBytes = bytes
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), responseBytes.toResponseBody())
        }.build()

        assertTrue(probeRepositorySource(client, sources.first(), sha))
        responseBytes = bytes.copyOf(bytes.size - 1)
        assertFalse(probeRepositorySource(client, sources.first(), sha))
        responseBytes = ByteArray(bytes.size) { '<'.code.toByte() }
        assertFalse(probeRepositorySource(client, sources.first(), sha))
        responseBytes = bytes + 1.toByte()
        assertFalse(probeRepositorySource(client, sources.first(), sha))
    }

    @Test
    fun `concurrent checks share a batch and session results never expire without manual retest`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var probes = 0
        val monitor = RepositorySourceHealthMonitor(sources, probe = {
            probes++
            gate.await()
            true
        })
        val one = launch { monitor.test(force = true) }
        runCurrent()
        val two = launch { monitor.test(force = true) }
        runCurrent()
        assertEquals(4, probes)
        assertTrue(monitor.health.value.values.all { it == RepositorySourceHealth.TESTING })
        gate.complete(Unit)
        advanceUntilIdle()
        one.join()
        two.join()
        monitor.test()
        assertEquals(4, probes)
        advanceTimeBy(365L * 24 * 60 * 60 * 1_000)
        monitor.test()
        assertEquals(4, probes)
        monitor.test(force = true)
        assertEquals(8, probes)
        assertTrue(monitor.health.value.values.all { it == RepositorySourceHealth.AVAILABLE })

        val newSession = RepositorySourceHealthMonitor(sources, probe = { probes++; true })
        assertTrue(newSession.health.value.values.all { it == RepositorySourceHealth.UNTESTED })
        newSession.test()
        assertEquals(12, probes)
    }

    @Test
    fun `cancelled checks restore unknown state without enabling fallback`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val monitor = RepositorySourceHealthMonitor(sources, probe = { gate.await(); true })
        val job = launch { monitor.test() }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(monitor.health.value.values.all { it == RepositorySourceHealth.UNTESTED })
        assertFalse(areRepositoryPrimarySourcesUnavailable(sources, monitor.health.value))
        gate.complete(Unit)
        monitor.test()
        assertTrue(monitor.health.value.values.all { it == RepositorySourceHealth.AVAILABLE })
    }

    @Test
    fun `expected probe IO failures mark sources unavailable`() = runTest {
        val monitor = RepositorySourceHealthMonitor(sources, probe = { throw IOException("offline") })
        monitor.test()
        assertTrue(areRepositoryPrimarySourcesUnavailable(sources, monitor.health.value))
    }

    @Test
    fun `real file failure visits all four regular sources then uses force even after successful probes`() = runBlocking {
        val visited = Collections.synchronizedList(mutableListOf<String>())
        val monitor = RepositorySourceHealthMonitor(sources, probe = { true })
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            visited += request.url.toString()
            when {
                request.url.queryParameter("f") == "1" -> response(request, "forced document".toResponseBody())
                    .newBuilder().header("X-Download-Type", "file").build()
                request.url.host.endsWith("muxyang.com") -> linkResponse(request)
                else -> response(request, "unavailable".toResponseBody(), 503)
            }
        }.build()

        val result = RepositoryDownloadTransport(client, servers, sources, monitor).read(
            "indexed-file", "https://raw.githubusercontent.com/example/repo/main/a.pdf",
            "https://cdn.jsdelivr.net/gh/example/repo@main/a.pdf", "ghfast"
        ) { response, _ -> response.body!!.string() }

        assertEquals("forced document", result)
        assertEquals(6, visited.size)
        assertTrue(visited[1].startsWith("https://ghfast.top/"))
        assertEquals(4, visited.drop(1).dropLast(1).map { it.substringBefore("/https://").substringAfter("://").substringBefore('/') }.distinct().size)
        assertEquals("https://de-ahutong-storage.muxyang.com/api/download?id=indexed-file&f=1", visited.last())
        assertTrue(areRepositoryPrimarySourcesUnavailable(sources, monitor.health.value))
    }

    @Test
    fun `one working regular source prevents force and preserves selected priority`() = runBlocking {
        val visited = Collections.synchronizedList(mutableListOf<String>())
        val monitor = RepositorySourceHealthMonitor(sources, probe = { true })
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            visited += chain.request().url.toString()
            if (chain.request().url.host.endsWith("muxyang.com")) linkResponse(chain.request())
            else response(chain.request(), "# markdown".toResponseBody())
        }.build()
        val result = RepositoryDownloadTransport(client, servers, sources, monitor).read(
            "a", "https://raw.githubusercontent.com/example/repo/main/a.md",
            "https://cdn.jsdelivr.net/gh/example/repo@main/a.md", "ghfast"
        ) { response, _ -> response.body!!.string() }
        assertEquals("# markdown", result)
        assertEquals(2, visited.size)
        assertTrue(visited.last().startsWith("https://ghfast.top/"))
        assertTrue(visited.none { it.contains("f=1") })
    }

    @Test
    fun `all failed speed checks skip regular URLs and force may fail over storage servers`() = runBlocking {
        val visited = Collections.synchronizedList(mutableListOf<String>())
        val monitor = RepositorySourceHealthMonitor(sources, probe = { false })
        monitor.test()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            visited += chain.request().url.toString()
            if (chain.request().url.host.startsWith("de-")) throw IOException("de offline")
            response(chain.request(), "# backup markdown".toResponseBody())
                .newBuilder().header("X-Download-Type", "file").build()
        }.build()
        val result = RepositoryDownloadTransport(client, servers, sources, monitor).read(
            "encoded+id", "https://raw.githubusercontent.com/example/repo/main/a.md",
            "https://cdn.jsdelivr.net/gh/example/repo@main/a.md", "jsdelivr"
        ) { response, _ -> response.body!!.string() }
        assertEquals("# backup markdown", result)
        assertEquals(2, visited.size)
        assertTrue(visited.all { it.endsWith("?id=encoded%2Bid&f=1") })
    }

    @Test
    fun `LFS server file is streamed directly without requesting any GitHub source`() = runBlocking {
        var requests = 0
        var probes = 0
        val monitor = RepositorySourceHealthMonitor(sources, probe = { probes++; true })
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            response(chain.request(), "large-lfs-data".toResponseBody()).newBuilder()
                .header("X-Download-Type", "file").header("X-File-Size", "14").build()
        }.build()
        var expectedSize = -1L
        val result = RepositoryDownloadTransport(client, servers, sources, monitor).read(
            "lfs", "https://raw.githubusercontent.com/example/repo/main/a.pdf",
            "https://cdn.jsdelivr.net/gh/example/repo@main/a.pdf", "jsdelivr"
        ) { response, size -> expectedSize = size; response.body!!.string() }
        assertEquals("large-lfs-data", result)
        assertEquals(14L, expectedSize)
        assertEquals(1, requests)
        assertEquals(0, probes)
    }

    @Test
    fun `failed forced downloads preserve causes and reject another link response`() = runBlocking {
        val monitor = RepositorySourceHealthMonitor(sources, probe = { false })
        monitor.test()
        val client = OkHttpClient.Builder().addInterceptor { chain -> linkResponse(chain.request()) }.build()
        val failure = assertFailsWith<IOException> {
            RepositoryDownloadTransport(client, servers, sources, monitor).read<String>(
                "a", "https://raw.githubusercontent.com/example/repo/main/a.pdf",
                "https://cdn.jsdelivr.net/gh/example/repo@main/a.pdf", "jsdelivr"
            ) { response, _ -> response.body!!.string() }
        }
        assertNotNull(failure.cause)
        assertEquals(1, failure.suppressed.size)
        assertTrue(failure.stackTraceToString().contains("备用源没有返回文件内容"))
    }

    @Test
    fun `forced source never writes JSON HTML or LFS pointer disguised as successful file`() = runBlocking {
        val monitor = RepositorySourceHealthMonitor(sources, probe = { false })
        monitor.test()
        val payloads = listOf(
            """{"url":"https://github.com/not-a-file"}""" to "application/octet-stream",
            """{"error":"origin unavailable"}""" to "application/json",
            "<!DOCTYPE html><html>gateway error</html>" to "application/octet-stream",
            "version https://git-lfs.github.com/spec/v1\noid sha256:abc\nsize 123" to "text/plain"
        )
        for ((payload, type) in payloads) {
            var readCalled = false
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                response(chain.request(), payload.toResponseBody(type.toMediaType()))
            }.build()
            assertFailsWith<IOException> {
                RepositoryDownloadTransport(client, servers, sources, monitor).read<String>(
                    "a", "https://raw.githubusercontent.com/example/repo/main/a.pdf",
                    "https://cdn.jsdelivr.net/gh/example/repo@main/a.pdf", "jsdelivr"
                ) { response, _ -> readCalled = true; response.body!!.string() }
            }
            assertFalse(readCalled)
        }
    }

    @Test
    fun `incomplete regular file body also reaches backup rather than returning a partial success`() = runBlocking {
        val monitor = RepositorySourceHealthMonitor(sources, probe = { true })
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            when {
                request.url.queryParameter("f") == "1" -> response(request, "complete".toResponseBody())
                    .newBuilder().header("X-Download-Type", "file").header("X-File-Size", "8").build()
                request.url.host.endsWith("muxyang.com") -> response(request,
                    """{"url":"https://raw.githubusercontent.com/example/repo/main/a.pdf","size":8}"""
                        .toResponseBody("application/json".toMediaType()))
                    .newBuilder().header("X-Download-Type", "link").build()
                else -> response(request, "short".toResponseBody())
            }
        }.build()
        val result = RepositoryDownloadTransport(client, servers, sources, monitor).read(
            "a", "https://raw.githubusercontent.com/example/repo/main/a.pdf",
            "https://cdn.jsdelivr.net/gh/example/repo@main/a.pdf", "jsdelivr"
        ) { response, expected -> response.body!!.bytes().takeIf { it.size.toLong() == expected }?.decodeToString() }
        assertEquals("complete", result)
    }

    @Test
    fun `explicit server file response permits legitimate JSON and HTML documents`() = runBlocking {
        val monitor = RepositorySourceHealthMonitor(sources, probe = { false })
        monitor.test()
        for ((name, content, type) in listOf(
            Triple("a.json", """{"message":"example data"}""", "application/json"),
            Triple("a.html", "<html>course materials</html>", "text/html")
        )) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                response(chain.request(), content.toResponseBody(type.toMediaType()))
                    .newBuilder().header("X-Download-Type", "file").build()
            }.build()
            assertEquals(content, RepositoryDownloadTransport(client, servers, sources, monitor).read(
                "a", "https://raw.githubusercontent.com/example/repo/main/$name",
                "https://cdn.jsdelivr.net/gh/example/repo@main/$name", "jsdelivr"
            ) { response, _ -> response.body!!.string() })
        }
    }

    @Test
    fun `unmarked mirror JSON and HTML are accepted only for the matching document extension`() = runBlocking {
        for ((name, content, type) in listOf(
            Triple("a.ipynb", """{"cells":[],"metadata":{}}""", "application/json"),
            Triple("a.html", "<!doctype html><html>course materials</html>", "text/html")
        )) {
            val monitor = RepositorySourceHealthMonitor(sources, probe = { true })
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                if (chain.request().url.host.endsWith("muxyang.com")) linkResponse(chain.request())
                else response(chain.request(), content.toResponseBody(type.toMediaType()))
            }.build()
            assertEquals(content, RepositoryDownloadTransport(client, servers, sources, monitor).read(
                "a", "https://raw.githubusercontent.com/example/repo/main/$name",
                "https://cdn.jsdelivr.net/gh/example/repo@main/$name", "jsdelivr"
            ) { response, _ -> response.body!!.string() })
        }
    }

    @Test
    fun `cancelling a body read closes the socket before the caller can clean up`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(64 * 1024)))
                .throttleBody(1_024L, 5L, TimeUnit.SECONDS))
            val startedReading = CountDownLatch(1)
            val job = launch(Dispatchers.IO) {
                readRepositoryResponse(OkHttpClient(), Request.Builder().url(server.url("/file")).build()) { response ->
                    response.body!!.byteStream().use { input ->
                        val first = ByteArray(1_024)
                        input.read(first)
                        startedReading.countDown()
                        input.readBytes()
                    }
                }
            }
            assertTrue(withContext(Dispatchers.IO) { startedReading.await(5, TimeUnit.SECONDS) })
            withTimeout(2_000L) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }

    @Test
    fun `storage permission failure stops every download lane without marking sources unavailable`() = runBlocking {
        for (lane in 0..2) {
            val monitor = RepositorySourceHealthMonitor(sources, probe = { lane != 2 })
            monitor.test()
            val initialHealth = monitor.health.value
            val visited = mutableListOf<String>()
            val denied = SecurityException("download directory revoked")
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                visited += request.url.toString()
                if (lane == 1 && request.url.host.endsWith("muxyang.com")) linkResponse(request)
                else response(request, "document".toResponseBody()).newBuilder()
                    .header("X-Download-Type", "file").build()
            }.build()

            assertFailsWith<SecurityException> {
                RepositoryDownloadTransport(client, servers, sources, monitor).read<String>(
                    "a", "https://raw.githubusercontent.com/example/repo/main/a.pdf",
                    "https://cdn.jsdelivr.net/gh/example/repo@main/a.pdf", "jsdelivr"
                ) { _, _ -> throw denied }
            }

            assertEquals(if (lane == 1) 2 else 1, visited.size)
            assertEquals(initialHealth, monitor.health.value)
        }
    }

    @Test
    fun `cancellation cancels the request and never enables force`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val monitor = RepositorySourceHealthMonitor(sources, probe = { true })
        var forced = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.queryParameter("f") == "1") forced = true
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            response(chain.request(), "data".toResponseBody())
        }.build()
        val job = async(Dispatchers.IO) {
            RepositoryDownloadTransport(client, servers, sources, monitor).read(
                "a", "https://raw.githubusercontent.com/example/repo/main/a.pdf",
                "https://cdn.jsdelivr.net/gh/example/repo@main/a.pdf", "jsdelivr"
            ) { response, _ -> response.body!!.string() }
        }
        assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        job.cancel()
        release.countDown()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(forced)
        assertTrue(monitor.health.value.values.all { it == RepositorySourceHealth.UNTESTED })
    }

    private fun response(request: okhttp3.Request, body: okhttp3.ResponseBody, code: Int = 200): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(code).message("test").body(body).build()

    private fun linkResponse(request: okhttp3.Request): Response = response(
        request,
        """{"url":"https://raw.githubusercontent.com/example/repo/main/document","size":0}"""
            .toResponseBody("application/json".toMediaType())
    ).newBuilder().header("X-Download-Type", "link").build()
}
