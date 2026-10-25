package com.ahu.ahutong.data.repository

import com.google.gson.Gson
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal const val REPOSITORY_PROBE_BYTES = 1_048_576L
internal const val REPOSITORY_PROBE_COMMIT = "8ff60f13f5c88a60a41b46fea01e316759a6011b"
internal const val REPOSITORY_PROBE_SHA256 = "f790cffe9ad224031a793c5ac906a5e40d9f86a7e386cc750f4361038066a667"

internal fun repositorySourceUrl(
    source: RepositoryAccelerationSource,
    rawUrl: String,
    cdnUrl: String
): String = when {
    source.isFallback -> error("备用源只能通过资料存储服务器下载")
    source.useJsDelivr -> cdnUrl
    source.proxyPrefix != null -> source.proxyPrefix + rawUrl
    else -> rawUrl
}

internal fun repositoryProbeUrl(source: RepositoryAccelerationSource): String = repositorySourceUrl(
    source,
    "https://raw.githubusercontent.com/MuxYang/SpeedTest/$REPOSITORY_PROBE_COMMIT/1MiB.bin",
    "https://cdn.jsdelivr.net/gh/MuxYang/SpeedTest@$REPOSITORY_PROBE_COMMIT/1MiB.bin"
)

/** 在存储接口上添加参数，保留已有查询及转义；不得给 GitHub 或代理地址加 force。 */
internal fun forceStorageDownloadUrl(url: String): String {
    val parsed = url.toHttpUrl()
    require(parsed.host.endsWith(".muxyang.com") && parsed.encodedPath == "/api/download") {
        "备用下载必须使用资料存储接口"
    }
    return parsed.newBuilder().setQueryParameter("f", "1").build().toString()
}

internal fun storageDownloadUrl(server: StorageServer, fileId: String, force: Boolean = false): String {
    val url = "${server.baseUrl}/api/download".toHttpUrl().newBuilder()
        .addQueryParameter("id", fileId)
        .build().toString()
    return if (force) forceStorageDownloadUrl(url) else url
}

/** cancellation 同时覆盖等待响应头和读取 body；完成清理后才允许调用方删除临时文件。 */
internal suspend fun <T> readRepositoryResponse(
    client: OkHttpClient,
    request: Request,
    read: (Response) -> T
): T {
    val call = client.newCall(request)
    val finished = CompletableDeferred<Unit>()
    try {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            val callback = object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    finished.complete(Unit)
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!continuation.isActive) return
                            val value = read(it)
                            if (continuation.isActive) continuation.resume(value)
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally {
                        finished.complete(Unit)
                    }
                }
            }
            try {
                call.enqueue(callback)
            } catch (error: Exception) {
                finished.complete(Unit)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    } finally {
        if (!finished.isCompleted) {
            call.cancel()
            withContext(NonCancellable) { finished.await() }
        }
    }
}

internal suspend fun probeRepositorySource(
    client: OkHttpClient,
    source: RepositoryAccelerationSource,
    expectedSha256: String = REPOSITORY_PROBE_SHA256
): Boolean =
    readRepositoryResponse(
        client,
        Request.Builder().url(repositoryProbeUrl(source)).header("Cache-Control", "no-cache").build()
    ) { response ->
        if (response.code != 200) return@readRepositoryResponse false
        val body = response.body ?: return@readRepositoryResponse false
        val declaredSize = body.contentLength()
        if (declaredSize >= 0L && declaredSize != REPOSITORY_PROBE_BYTES) return@readRepositoryResponse false
        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        body.byteStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                received += count
                if (received > REPOSITORY_PROBE_BYTES) return@readRepositoryResponse false
                digest.update(buffer, 0, count)
            }
        }
        received == REPOSITORY_PROBE_BYTES &&
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == expectedSha256
    }

internal class RepositorySourceHealthMonitor(
    private val sources: List<RepositoryAccelerationSource>,
    private val probe: suspend (RepositoryAccelerationSource) -> Boolean
) {
    private val primary = sources.filterNot { it.isFallback }
    private val mutableHealth = MutableStateFlow(primary.associate { it.id to RepositorySourceHealth.UNTESTED })
    val health: StateFlow<Map<String, RepositorySourceHealth>> = mutableHealth.asStateFlow()
    private val mutex = Mutex()
    @Volatile private var generation = 0L

    suspend fun test(force: Boolean = false) {
        val requestedGeneration = generation
        mutex.withLock {
            // 排队期间另一调用已完成检测，即使 force 也复用该批结果。
            if (generation != requestedGeneration) return
            // 单例随 App 进程存活；自动检测本 session 只需完整执行一次，不按时间过期。
            if (!force && generation > 0L) return
            val previous = mutableHealth.value
            mutableHealth.value = primary.associate { it.id to RepositorySourceHealth.TESTING }
            try {
                coroutineScope {
                    primary.map { source ->
                        async {
                            val available = try {
                                probe(source)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: IOException) {
                                false
                            }
                            record(source.id, available)
                        }
                    }.awaitAll()
                }
                generation++
            } finally {
                mutableHealth.update { current ->
                    current.mapValues { (id, value) ->
                        if (value == RepositorySourceHealth.TESTING) {
                            previous[id] ?: RepositorySourceHealth.UNTESTED
                        } else value
                    }
                }
            }
        }
    }

    fun record(sourceId: String, available: Boolean) {
        mutableHealth.update { current ->
            if (sourceId !in current) current else current + (
                sourceId to if (available) RepositorySourceHealth.AVAILABLE else RepositorySourceHealth.UNAVAILABLE
            )
        }
    }
}

/** 下载和 Markdown 共用回退顺序，避免某条路径遗忘自动备用源。 */
internal class RepositoryDownloadTransport(
    private val client: OkHttpClient,
    private val servers: List<StorageServer>,
    private val sources: List<RepositoryAccelerationSource>,
    private val monitor: RepositorySourceHealthMonitor,
    private val gson: Gson = Gson()
) {
    suspend fun <T : Any> read(
        fileId: String,
        rawUrl: String,
        cdnUrl: String,
        preferredSourceId: String,
        read: (Response, Long) -> T?
    ): T {
        val errors = mutableListOf<Exception>()
        var allDownloadSourcesFailed = false
        // 全挂时复用本进程结果；恢复常规源由用户手动重测确认。
        if (areRepositoryPrimarySourcesUnavailable(sources, monitor.health.value)) monitor.test()
        if (!areRepositoryPrimarySourcesUnavailable(sources, monitor.health.value)) {
            var link: StorageLinkResponse? = null
            for (server in servers) {
                try {
                    val result = readRepositoryResponse(
                        client, Request.Builder().url(storageDownloadUrl(server, fileId)).build()
                    ) { response ->
                        requireSuccessfulDownload(response)
                        if (response.header("X-Download-Type") == "link") {
                            link = gson.fromJson(response.body!!.string(), StorageLinkResponse::class.java)
                                ?: throw IOException("资料下载地址为空")
                            null
                        } else {
                            requireFileDownload(response, rawUrl)
                            read(response, response.header("X-File-Size")?.toLongOrNull()
                                ?: response.body!!.contentLength())
                                ?: throw IOException("资料文件未完整下载")
                        }
                    }
                    if (result != null) return result
                    if (link != null) break
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (error is SecurityException) throw error
                    errors += IOException("${server.id} 资料接口失败", error)
                }
            }
            if (link != null) {
                // 初次下载也检测：打开文件不依赖用户先访问设置页。
                monitor.test()
                if (!areRepositoryPrimarySourcesUnavailable(sources, monitor.health.value)) {
                    val primary = sources.filterNot { it.isFallback }
                        .sortedBy { if (it.id == preferredSourceId) 0 else 1 }
                    for (source in primary) {
                        try {
                            val value = readRepositoryResponse(
                                client,
                                Request.Builder().url(repositorySourceUrl(source, link!!.url.ifBlank { rawUrl }, cdnUrl)).build()
                            ) { response ->
                                requireSuccessfulDownload(response)
                                requireFileDownload(response, rawUrl)
                                read(response, link!!.size.takeIf { it > 0L } ?: response.body!!.contentLength())
                                    ?: throw IOException("资料文件未完整下载")
                            }
                            monitor.record(source.id, true)
                            return value
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            if (error is SecurityException) throw error
                            monitor.record(source.id, false)
                            errors += IOException("${source.name} 下载失败", error)
                        }
                    }
                    allDownloadSourcesFailed = primary.isNotEmpty()
                }
            } else {
                // 元数据接口失败时，只有确认四个常规源全挂后才可请求强制接口。
                monitor.test()
            }
        }
        if (allDownloadSourcesFailed || areRepositoryPrimarySourcesUnavailable(sources, monitor.health.value)) {
            for (server in servers) {
                try {
                    return readRepositoryResponse(
                        client, Request.Builder().url(storageDownloadUrl(server, fileId, force = true)).build()
                    ) { response ->
                        requireSuccessfulDownload(response)
                        if (response.header("X-Download-Type") == "link") {
                            throw IOException("备用源没有返回文件内容")
                        }
                        requireFileDownload(response, rawUrl, requireStorageFileType = true)
                        read(response, response.header("X-File-Size")?.toLongOrNull()
                            ?: response.body!!.contentLength())
                            ?: throw IOException("备用源文件未完整下载")
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (error is SecurityException) throw error
                    errors += IOException("${server.id} 备用源下载失败", error)
                }
            }
        }
        val failure = IOException("学习资料下载失败，请检查网络后重试", errors.lastOrNull())
        errors.dropLast(1).forEach(failure::addSuppressed)
        throw failure
    }

    private fun requireSuccessfulDownload(response: Response) {
        // 此路径没有发 Range；206 只能是意外的部分内容，不能标记为完整文件。
        if (response.code != 200) throw IOException("HTTP ${response.code}")
        if (response.body == null) throw IOException("资料下载响应为空")
    }

    private fun requireFileDownload(response: Response, rawUrl: String, requireStorageFileType: Boolean = false) {
        val downloadType = response.header("X-Download-Type")
        if (requireStorageFileType && downloadType != "file") {
            throw IOException("备用源没有返回文件内容")
        }
        if (downloadType != null && downloadType != "file") {
            throw IOException("下载接口没有返回文件: $downloadType")
        }
        // 存储服务器明确确认的 file 可以是 JSON/HTML 资料，本身不是错误。
        if (downloadType == "file") return
        val extension = rawUrl.toHttpUrl().pathSegments.lastOrNull().orEmpty().substringAfterLast('.', "").lowercase()
        val expectsHtml = extension in setOf("html", "htm")
        val expectsJson = extension in setOf("json", "ipynb")
        val subtype = response.body!!.contentType()?.subtype.orEmpty()
        if (!expectsJson && (subtype.equals("json", ignoreCase = true) || subtype.endsWith("+json", ignoreCase = true))) {
            throw IOException("下载接口返回了 JSON，未返回文件")
        }
        if (!expectsHtml && subtype.equals("html", ignoreCase = true)) {
            throw IOException("下载源返回了网页，未返回文件")
        }
        // 一些网关错误缺少 Content-Type，读取少量前缀也不能消耗实际下载流。
        val prefix = response.peekBody(512).string().trimStart('\uFEFF', ' ', '\t', '\n', '\r')
        if (!expectsHtml && (prefix.startsWith("<!doctype html", ignoreCase = true) || prefix.startsWith("<html", ignoreCase = true))) {
            throw IOException("下载源返回了网页，未返回文件")
        }
        if (!expectsJson && Regex("""^\{\s*"(?:url|error|message|code)"\s*:""").containsMatchIn(prefix)) {
            throw IOException("下载接口返回了错误或链接，未返回文件")
        }
        if (prefix.startsWith("version https://git-lfs.github.com/spec/v1")) {
            throw IOException("下载源返回了 Git LFS 指针，未返回文件")
        }
    }
}
