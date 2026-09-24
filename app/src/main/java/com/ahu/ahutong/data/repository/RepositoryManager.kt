package com.ahu.ahutong.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.dao.PreferencesManager
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.OkHttpClient
import com.ahu.ahutong.data.network.AhuHttp

internal object RepositoryIndexRefreshPolicy {
    const val AUTO_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1_000L

    fun canReuse(
        cachedAtMillis: Long,
        cachedVersion: Int,
        expectedVersion: Int,
        hasRootContents: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val age = nowMillis - cachedAtMillis
        return cachedAtMillis > 0L &&
            age >= 0L &&
            age < AUTO_REFRESH_INTERVAL_MS &&
            cachedVersion == expectedVersion &&
            hasRootContents
    }

    fun canReuseServerIndex(
        serverUpdatedAt: Long,
        cachedServerUpdatedAt: Long,
        hasRootContents: Boolean
    ): Boolean = serverUpdatedAt > 0L &&
        serverUpdatedAt == cachedServerUpdatedAt &&
        hasRootContents
}

internal data class StorageServer(val id: String, val baseUrl: String)

internal sealed interface StorageIndexResolution {
    val server: StorageServer

    data class Reused(
        override val server: StorageServer,
        val updatedAt: Long
    ) : StorageIndexResolution

    data class Fetched(
        override val server: StorageServer,
        val index: StorageIndexResponse
    ) : StorageIndexResolution
}

internal suspend fun resolveStorageIndex(
    servers: List<StorageServer>,
    cachedServerId: String?,
    cachedTimestamp: (String) -> Long,
    hasRootContents: Boolean,
    fetchUpdatedAt: suspend (StorageServer) -> Long,
    fetchIndex: suspend (StorageServer) -> StorageIndexResponse
): StorageIndexResolution {
    var lastError: Exception? = null
    for (server in servers) {
        try {
            val updatedAt = fetchUpdatedAt(server)
            require(updatedAt > 0L) { "${server.id} 索引更新时间无效" }
            if (server.id == cachedServerId && RepositoryIndexRefreshPolicy.canReuseServerIndex(
                    updatedAt, cachedTimestamp(server.id), hasRootContents
                )
            ) {
                return StorageIndexResolution.Reused(server, updatedAt)
            }
            val index = fetchIndex(server)
            require(index.updatedAt > 0L) { "${server.id} 索引时间戳无效" }
            return StorageIndexResolution.Fetched(server, index)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            lastError = error
        }
    }
    throw IllegalStateException("索引服务器均不可用", lastError)
}

internal fun <T> readStorageDownload(
    client: OkHttpClient,
    servers: List<StorageServer>,
    fileId: String,
    read: (Response) -> T?
): T? {
    for (server in servers) {
        try {
            client.newCall(Request.Builder().url("${server.baseUrl}/api/download?id=$fileId").build())
                .execute().use { response ->
                    if (response.isSuccessful) read(response)?.let { return it }
                }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 当前节点请求或读取失败，继续尝试另一节点。
        }
    }
    return null
}

internal fun readStorageIndex(
    body: ResponseBody,
    gson: Gson,
    onDownloadProgress: ((Long, Long) -> Unit)? = null
): StorageIndexResponse {
    val totalBytes = body.contentLength()
    val bytes = ByteArrayOutputStream()
    onDownloadProgress?.invoke(0L, totalBytes)
    body.byteStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            bytes.write(buffer, 0, count)
            onDownloadProgress?.invoke(bytes.size().toLong(), totalBytes)
        }
    }
    return gson.fromJson(bytes.toString(Charsets.UTF_8.name()), StorageIndexResponse::class.java)
        ?: throw IllegalStateException("资料索引解析失败")
}

object RepositoryManager {
    private const val RAW_HOST = "https://raw.githubusercontent.com"
    private const val GITHUB_HOST = "https://github.com"
    private const val CDN_HOST = "https://cdn.jsdelivr.net/gh"
    private const val CONTENT_CACHE_PREFIX = "content_cache_"
    private const val CONTENT_CACHE_TIME_PREFIX = "content_cache_time_"
    private const val CONTENT_TREE_CACHE_TIME_KEY = "content_tree_cache_time"
    private const val CONTENT_TREE_CACHE_VERSION_KEY = "content_tree_cache_version"
    private const val CONTENT_UNSUPPORTED_PATHS_KEY = "content_unsupported_paths"
    private const val CONTENT_CACHE_VERSION = 9
    private const val DOWNLOAD_RECORDS_KEY = "downloaded_files"
    private const val DOWNLOAD_RELATIVE_ROOT = "ahutong"
    private const val CONTENT_INDEX_SERVER_ID_KEY = "content_index_server_id"
    private const val CONTENT_INDEX_SERVER_TS_PREFIX = "content_index_server_ts_"

    // de 为主要节点，hk 为备用节点；非 LFS 文件的最终下载地址仍是 GitHub 直链。
    private val storageServers = listOf(
        StorageServer("de", "https://de-ahutong-storage.muxyang.com"),
        StorageServer("hk", "https://hk-ahutong-storage.muxyang.com")
    )
    private const val HEADER_DOWNLOAD_TYPE = "X-Download-Type"
    private const val HEADER_FILE_SIZE = "X-File-Size"
    private const val DOWNLOAD_TYPE_LINK = "link"
    private const val DIR_STATE_UNSUPPORTED = "unsupported"

    private val repositorySources = listOf(
        RepositorySource(
            id = "cs",
            title = "计算机科学与技术学院",
            owner = "Kaltsit-cell",
            repo = "AHU-CS-Repository",
            branch = "master"
        ),
        RepositorySource(
            id = "ai",
            title = "人工智能学院",
            owner = "DylanAo",
            repo = "AHU-AI-Repository",
            branch = "main"
        ),
        RepositorySource(
            id = "ic",
            title = "集成电路学院",
            owner = "Tonyseth",
            repo = "AHU-IC-Design-personal-Repository",
            branch = "main"
        ),
        RepositorySource(
            id = "ee",
            title = "电子信息工程学院",
            owner = "HarryWeasley3",
            repo = "AHU-EE-Repository",
            branch = "main"
        ),
        RepositorySource(
            id = "internet",
            title = "互联网学院",
            owner = "Zeraora-807",
            repo = "AHU-Internet-Exams-Archive",
            branch = "main"
        ),
        RepositorySource(
            id = "sbi",
            title = "石溪学院",
            owner = "UponNoise",
            repo = "AHU_SBI_DMT",
            branch = "main"
        )
    )
    private val repositorySourceById = repositorySources.associateBy { it.id }
    private val repositoryTitleById = repositorySources.associate { it.id to it.title }

    val accelerationSources = listOf(
        RepositoryAccelerationSource(
            id = "jsdelivr",
            name = "jsDelivr",
            description = "默认优先使用 jsDelivr CDN",
            useJsDelivr = true
        ),
        RepositoryAccelerationSource(
            id = "moeyy",
            name = "Moeyy",
            description = "通过 github.moeyy.xyz 加速 GitHub 原始文件",
            proxyPrefix = "https://github.moeyy.xyz/"
        ),
        RepositoryAccelerationSource(
            id = "gh-proxy",
            name = "gh-proxy",
            description = "通过 gh-proxy.com 加速 GitHub 原始文件",
            proxyPrefix = "https://gh-proxy.com/"
        ),
        RepositoryAccelerationSource(
            id = "direct",
            name = "GitHub 直连",
            description = "不使用加速源，直接连接 GitHub"
        )
    )

    private val gson = Gson()
    private val kv: MMKV by lazy {
        MMKV.initialize(AppEnvironmentHolder.context())
        MMKV.mmkvWithID("repository_downloads")
    }
    private val warmUpMutex = Mutex()

    private val downloadClient = AhuHttp.plain(
        connectTimeoutSeconds = 10,
        readTimeoutSeconds = 60
    ).build()
    private val indexClient = AhuHttp.plain(
        connectTimeoutSeconds = 15,
        readTimeoutSeconds = 120,
        callTimeoutSeconds = 180
    ).build()

    // === GitHub API ===

    suspend fun getContents(path: String = "", forceRefresh: Boolean = false): List<GitHubContentItem> {
        return withContext(Dispatchers.IO) {
            val fallbackRootItems = if (path.isBlank()) repositoryRootItems() else null

            if (!forceRefresh) {
                getCachedContents(path)?.items?.let { return@withContext it }

                // The screen starts the full index warm-up independently. On a cold install the
                // six repository trees should not block the first frame: the virtual repository
                // roots are already known locally and can be rendered immediately while the
                // detailed directory index is built on Dispatchers.IO.
                fallbackRootItems?.let { return@withContext it }
            }

            runCatching {
                warmUpAllContentCaches(
                    forceRefresh = forceRefresh || getCachedContents(path) == null
                )
            }.onSuccess {
                getCachedContents(path)?.items?.let { return@withContext it }
            }.onFailure {
                fallbackRootItems?.let { return@withContext it }
            }

            getCachedContents(path)?.items?.let { return@withContext it }
            fallbackRootItems?.let { return@withContext it }

            throw IllegalStateException("目录缓存不可用")
        }
    }

    suspend fun warmUpAllContentCaches(
        forceRefresh: Boolean = false,
        onProgress: ((Int) -> Unit)? = null,
        onDownloadProgress: ((Long, Long) -> Unit)? = null
    ): Long {
        return withContext(Dispatchers.IO) {
            warmUpMutex.withLock {
                val cachedUpdateTime = kv.decodeLong(CONTENT_TREE_CACHE_TIME_KEY, 0L)
                val cachedVersion = kv.decodeInt(CONTENT_TREE_CACHE_VERSION_KEY, 0)
                val cachedServerId = kv.decodeString(CONTENT_INDEX_SERVER_ID_KEY)
                val hasUsableFreshCache = storageServers.any { it.id == cachedServerId } && RepositoryIndexRefreshPolicy.canReuse(
                    cachedAtMillis = cachedUpdateTime,
                    cachedVersion = cachedVersion,
                    expectedVersion = CONTENT_CACHE_VERSION,
                    hasRootContents = getCachedContents("") != null
                )
                if (!forceRefresh &&
                    hasUsableFreshCache
                ) {
                    return@withLock cachedUpdateTime
                }

                // 先问服务端索引时间戳：与本地记录一致说明索引没变，直接复用目录缓存，
                // 不必整包重新拉取 /api/list。
                val resolution = resolveStorageIndex(
                    servers = storageServers,
                    cachedServerId = cachedServerId,
                    cachedTimestamp = { serverId ->
                        kv.decodeLong("$CONTENT_INDEX_SERVER_TS_PREFIX$serverId", 0L)
                    },
                    hasRootContents = getCachedContents("") != null,
                    fetchUpdatedAt = ::fetchStorageUpdatedAt,
                    fetchIndex = { server -> fetchStorageIndex(server, onDownloadProgress) }
                )
                if (resolution is StorageIndexResolution.Reused) {
                    kv.encode(CONTENT_TREE_CACHE_TIME_KEY, System.currentTimeMillis())
                    return@withLock resolution.updatedAt
                }

                val fetched = resolution as StorageIndexResolution.Fetched
                val index = fetched.index
                val grouped = buildAllDirectoryCaches(index, onProgress)
                val updateTime = System.currentTimeMillis()
                grouped.forEach { (path, items) ->
                    saveContentCache(path, items, updateTime)
                }
                kv.encode(CONTENT_TREE_CACHE_TIME_KEY, updateTime)
                kv.encode(CONTENT_TREE_CACHE_VERSION_KEY, CONTENT_CACHE_VERSION)
                kv.encode("$CONTENT_INDEX_SERVER_TS_PREFIX${fetched.server.id}", index.updatedAt)
                kv.encode(CONTENT_INDEX_SERVER_ID_KEY, fetched.server.id)
                updateTime
            }
        }
    }

    fun getCachedContents(path: String = ""): CachedRepositoryContents? {
        if (kv.decodeInt(CONTENT_TREE_CACHE_VERSION_KEY, 0) != CONTENT_CACHE_VERSION) {
            return null
        }
        val key = contentCacheKey(path)
        val json = kv.decodeString("$CONTENT_CACHE_PREFIX$key", null) ?: return null
        val cached = try {
            val type = object : TypeToken<List<GitHubContentItem>>() {}.type
            val items: List<GitHubContentItem> = gson.fromJson(json, type)
            CachedRepositoryContents(
                items = items,
                updateTime = kv.decodeLong("$CONTENT_CACHE_TIME_PREFIX$key", 0L)
            )
        } catch (e: Exception) {
            null
        } ?: return null

        if (normalizeRepositoryPath(path).isEmpty() && !matchesCurrentRepositorySources(cached.items)) {
            return null
        }

        if (cached.items.any { it.name.contains('/') || it.name.contains('\\') }) {
            return null
        }

        return cached
    }

    fun shouldShowUnsupportedDirectoryMessage(path: String): Boolean {
        if (path.isBlank()) return false
        return getUnsupportedDirectoryPaths().contains(normalizeRepositoryPath(path))
    }

    fun getDirectorySummaries(items: List<GitHubContentItem>): Map<String, RepositoryDirectorySummary> {
        return items
            .filter { it.type == "dir" }
            .associate { item ->
                val cachedChildren = getCachedContents(item.path)?.items.orEmpty()
                item.path to RepositoryDirectorySummary(
                    directoryCount = cachedChildren.count { it.type == "dir" },
                    fileCount = cachedChildren.count { it.type == "file" }
                )
            }
    }

    fun getRawUrl(path: String): String {
        val resolved = resolveVirtualPath(path) ?: return ""
        return resolved.source.rawUrl(resolved.repositoryPath)
    }

    fun getGitHubUrl(path: String): String {
        val resolved = resolveVirtualPath(path) ?: return repositoryRootUrl()
        return resolved.source.githubUrl(resolved.repositoryPath, tree = true)
    }

    fun getRepositoryTitle(repoId: String): String {
        return repositoryTitleById[repoId] ?: repoId
    }

    fun getRepositoryOrder(path: String): Int {
        val repositoryId = normalizeRepositoryPath(path).substringBefore('/', "")
        val index = repositorySources.indexOfFirst { it.id == repositoryId }
        return if (index >= 0) index else Int.MAX_VALUE
    }

    fun formatDisplayPath(path: String): String {
        val normalized = normalizeRepositoryPath(path)
        if (normalized.isBlank()) return "学习资料"
        val segments = normalized.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return "学习资料"
        val repositoryId = segments.first()
        val repositoryTitle = getRepositoryTitle(repositoryId)
        val displaySegments = buildList {
            add(repositoryTitle)
            val relativeSegments = segments.drop(1)
            val trimmedRelativeSegments = if (relativeSegments.firstOrNull() == repositoryTitle) {
                relativeSegments.drop(1)
            } else {
                relativeSegments
            }
            addAll(trimmedRelativeSegments)
        }
        return displaySegments.joinToString("/")
    }

    suspend fun getMarkdownDocument(path: String): RepositoryMarkdownDocument {
        return withContext(Dispatchers.IO) {
            val resolved = resolveVirtualPath(path)
                ?: throw IllegalArgumentException("无效的 Markdown 路径")
            val selectedAccelerationSource = getSelectedAccelerationSource(AppEnvironmentHolder.context())
            val fileId = resolveStorageFileId(path)
                ?: throw IllegalStateException("索引中不存在该文件")

            val content = withStorageDownloadResponse(fileId) { apiResponse ->
                when (apiResponse.header(HEADER_DOWNLOAD_TYPE)) {
                    DOWNLOAD_TYPE_LINK -> {
                        val rawUrl = parseStorageLink(apiResponse)
                            ?: resolved.source.rawUrl(resolved.repositoryPath)
                        fetchTextWithFallback(
                            prioritizedDownloadUrls(rawUrl, resolved, selectedAccelerationSource)
                        )
                    }
                    // file：服务端已回源 LFS 并直接传输内容
                    else -> apiResponse.body?.string()
                }
            } ?: throw IllegalStateException("无法读取 Markdown")
            RepositoryMarkdownDocument(
                title = File(resolved.repositoryPath).name.ifBlank { "Markdown" },
                path = path,
                content = content
            )
        }
    }

    /**
     * 生成按当前加速源排序的候选下载地址：选中的源优先，jsDelivr 与 GitHub 直连兜底。
     * rawUrl 来自服务端 /api/download 的 link 应答，客户端只负责拼接加速前缀。
     */
    private fun prioritizedDownloadUrls(
        rawUrl: String,
        resolved: ResolvedRepositoryPath,
        selectedSource: RepositoryAccelerationSource
    ): List<String> {
        val cdnUrl = resolved.source.cdnUrl(resolved.repositoryPath)
        val selectedUrl = when {
            selectedSource.useJsDelivr -> cdnUrl
            selectedSource.proxyPrefix != null -> selectedSource.proxyPrefix + rawUrl
            else -> rawUrl
        }
        return listOf(selectedUrl, cdnUrl, rawUrl).distinct()
    }

    // === 下载管理 ===

    fun getDownloadedFiles(context: Context): List<DownloadedFile> {
        val records = getDownloadRecords()
        val files = records.mapNotNull { record ->
            record.toDownloadedFile(context) ?: run {
                removeDownloadRecord(record.path)
                null
            }
        }
        return files.sortedByDescending { it.downloadTime }
    }

    fun getDownloadedFile(path: String, context: Context): DownloadedFile? {
        val record = getDownloadRecords().firstOrNull { it.path == path } ?: return null
        return record.toDownloadedFile(context) ?: run {
            removeDownloadRecord(path)
            null
        }
    }

    fun isDownloaded(path: String, context: Context): Boolean {
        return getDownloadedFile(path, context) != null
    }

    suspend fun downloadFile(
        path: String,
        context: Context,
        onProgress: (Float) -> Unit = {}
    ): DownloadedFile? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val resolved = resolveVirtualPath(path) ?: return@withContext null
        val fileId = resolveStorageFileId(path) ?: return@withContext null
        val selectedAccelerationSource = getSelectedAccelerationSource(appContext)
        val previousRecord = getDownloadRecord(path)
        val target = createDownloadTarget(path, appContext)

        try {
            val downloaded = withStorageDownloadResponse(fileId) { apiResponse ->
                when (apiResponse.header(HEADER_DOWNLOAD_TYPE)) {
                    DOWNLOAD_TYPE_LINK -> {
                        // link：服务端返回 GitHub 直链，客户端按当前加速源拼接后下载
                        val rawUrl = parseStorageLink(apiResponse)
                            ?: resolved.source.rawUrl(resolved.repositoryPath)
                        val urls = prioritizedDownloadUrls(rawUrl, resolved, selectedAccelerationSource)
                        for (url in urls) {
                            val downloaded = runCatching {
                                downloadClient.newCall(Request.Builder().url(url).build())
                                    .execute().use { response ->
                                        if (!response.isSuccessful) return@use null
                                        streamResponseToTarget(
                                            response = response,
                                            target = target,
                                            totalBytes = response.body?.contentLength() ?: -1L,
                                            onProgress = onProgress
                                        )
                                    }
                            }.getOrNull()
                            if (downloaded != null) {
                                return@withStorageDownloadResponse downloaded
                            }
                        }
                        null
                    }
                    else -> {
                        // file：LFS 文件由服务端直接传输，X-File-Size 给出总大小
                        val totalBytes = apiResponse.header(HEADER_FILE_SIZE)?.toLongOrNull()
                            ?: apiResponse.body?.contentLength() ?: -1L
                        streamResponseToTarget(apiResponse, target, totalBytes, onProgress)
                    }
                }
            }
            if (downloaded == null) {
                target.delete()
                return@withContext null
            }
            completeDownload(path, target, previousRecord, appContext, downloaded)
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: Exception) {
            target.delete()
            return@withContext null
        }
    }

    private fun <T> withStorageDownloadResponse(
        fileId: String,
        read: (Response) -> T?
    ): T? = readStorageDownload(
        downloadClient,
        storageServers,
        fileId,
        read
    )

    private fun parseStorageLink(response: Response): String? {
        val link = runCatching {
            gson.fromJson(response.body?.string().orEmpty(), StorageLinkResponse::class.java)
        }.getOrNull()
        return link?.url?.takeIf { it.isNotBlank() }
    }

    private suspend fun fetchStorageUpdatedAt(server: StorageServer): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${server.baseUrl}/api/update")
            .header("Cache-Control", "no-cache")
            .build()
        indexClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("无法获取索引版本: HTTP ${response.code}")
            }
            gson.fromJson(response.body?.string().orEmpty(), StorageUpdateResponse::class.java)
                ?.updatedAt ?: 0L
        }
    }

    private suspend fun fetchStorageIndex(
        server: StorageServer,
        onDownloadProgress: ((Long, Long) -> Unit)? = null
    ): StorageIndexResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${server.baseUrl}/api/list")
            .header("Cache-Control", "no-cache")
            .build()
        indexClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("无法获取资料索引: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("资料索引响应为空")
            val index = readStorageIndex(body, gson, onDownloadProgress)
            val indexedRepositories = index.repositories.mapTo(mutableSetOf()) { it.id }
            val missingRepositories = repositorySources.filterNot { it.id in indexedRepositories }
            require(missingRepositories.isEmpty()) {
                "${server.id} 索引缺少仓库: ${missingRepositories.joinToString { it.title }}"
            }
            index
        }
    }

    /** 从目录缓存里找文件的服务端索引 id；找不到时强制刷新一次索引再试。 */
    private suspend fun resolveStorageFileId(path: String): String? {
        findStorageFileId(path)?.let { return it }
        runCatching { warmUpAllContentCaches(forceRefresh = true) }
        return findStorageFileId(path)
    }

    private fun findStorageFileId(path: String): String? {
        val parent = normalizeRepositoryPath(path).substringBeforeLast('/', "")
        return getCachedContents(parent)?.items?.firstOrNull { it.path == path }?.id
    }

    private fun fetchTextWithFallback(urls: List<String>): String? {
        for (url in urls) {
            val content = runCatching {
                downloadClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            }.getOrNull()
            if (content != null) return content
        }
        return null
    }

    /** 把响应体写入下载目标并回报进度，返回写入字节数。 */
    private fun streamResponseToTarget(
        response: Response,
        target: DownloadTarget,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ): Long? {
        val body = response.body ?: return null
        var downloadedBytes = 0L
        body.byteStream().use { input ->
            target.openOutputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var read = input.read(buffer)
                while (read >= 0) {
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    if (totalBytes > 0) {
                        onProgress(downloadedBytes.toFloat() / totalBytes.toFloat())
                    }
                    read = input.read(buffer)
                }
                output.flush()
            }
        }
        return downloadedBytes.takeIf { totalBytes <= 0L || it == totalBytes }
    }

    private fun completeDownload(
        path: String,
        target: DownloadTarget,
        previousRecord: DownloadRecord?,
        context: Context,
        downloadedBytes: Long
    ): DownloadedFile? {
        target.markCompleted()
        if (target is DownloadTarget.MediaStoreTarget || previousRecord?.uri != null) {
            previousRecord?.delete(context)
        }
        removeDownloadRecord(path)
        val record = DownloadRecord(
            path = path,
            localName = target.relativePath,
            uri = target.uri?.toString(),
            localPath = target.displayPath,
            downloadTime = System.currentTimeMillis(),
            size = downloadedBytes
        )
        saveDownloadRecord(record)
        return record.toDownloadedFile(context)
    }

    fun getLocalFile(path: String, context: Context): File? {
        val file = getDownloadedFile(path, context) ?: return null
        val localFile = File(file.localPath)
        return if (localFile.exists()) localFile else null
    }

    fun deleteFile(path: String, context: Context): Boolean {
        val record = getDownloadRecords().firstOrNull { it.path == path } ?: return false
        val deleted = record.delete(context)
        if (deleted) {
            removeDownloadRecord(path)
        }
        return deleted
    }

    // === 内部方法 ===

    private fun buildAllDirectoryCaches(
        index: StorageIndexResponse,
        onProgress: ((Int) -> Unit)? = null
    ): Map<String, List<GitHubContentItem>> {
        if (index.repositories.isEmpty() && index.files.isEmpty()) {
            // 服务端尚未完成首次同步时索引为空；不缓存空索引，按失败处理让界面走重试。
            throw IllegalStateException("资料索引为空")
        }

        val progressCounter = AtomicInteger(0)
        val grouped = mutableMapOf("" to repositoryRootItems())
        val unsupportedPaths = mutableSetOf<String>()
        repositorySources.forEach { source ->
            if (index.repositories.none { it.id == source.id }) {
                // 服务端暂时没同步到该仓库：不生成目录缓存，进入时会提示加载失败而不是空目录。
                return@forEach
            }
            val cache = buildDirectoryCachesFromStorageIndex(source, index)
            grouped.putAll(cache.grouped)
            unsupportedPaths += cache.unsupportedDirectoryPaths
            onProgress?.invoke(progressCounter.addAndGet(cache.documentFileCount))
        }
        saveUnsupportedDirectoryPaths(unsupportedPaths)
        return grouped
    }

    private fun repositoryRootItems(): List<GitHubContentItem> {
        return repositorySources.map { source ->
            GitHubContentItem(
                name = source.title,
                path = source.id,
                type = "dir",
                htmlUrl = source.githubUrl("", tree = true),
                repositoryId = source.id,
                repositoryPath = ""
            )
        }
    }

    /**
     * 把服务端索引映射成按目录分组的缓存条目。目录展示规则与旧版 GitHub tree 实现一致：
     * 子树内有文档的目录正常展示；只有不支持格式的目录作为「暂不支持」占位。
     */
    private fun buildDirectoryCachesFromStorageIndex(
        source: RepositorySource,
        index: StorageIndexResponse
    ): RepositoryIndexCache {
        val files = index.files.filter { it.repo == source.id }
        val dirStates = index.dirs
            .filter { it.repo == source.id }
            .associate { normalizeRepositoryPath(it.path) to it.state }

        val filesByParent = files.groupBy { normalizeRepositoryPath(it.path).substringBeforeLast('/', "") }
        val childDirsByParent = mutableMapOf<String, MutableList<String>>()
        dirStates.keys.forEach { dirPath ->
            val parent = dirPath.substringBeforeLast('/', "")
            childDirsByParent.getOrPut(parent) { mutableListOf() }.add(dirPath)
        }

        val grouped = mutableMapOf<String, List<GitHubContentItem>>()
        val unsupportedDirectoryPaths = mutableSetOf<String>()

        // 仓库根（""）不在服务端 dirs 里，补上以容纳直接放在根目录的文档。
        (dirStates.keys + "").forEach { dirPath ->
            val fileItems = filesByParent[dirPath].orEmpty().map { entry ->
                val repositoryPath = normalizeRepositoryPath(entry.path)
                GitHubContentItem(
                    name = entry.name,
                    path = virtualPath(source, repositoryPath),
                    type = "file",
                    size = entry.size,
                    downloadUrl = source.rawUrl(repositoryPath),
                    htmlUrl = source.githubUrl(repositoryPath, tree = false),
                    repositoryId = source.id,
                    repositoryPath = repositoryPath,
                    id = entry.id
                )
            }
            val dirItems = childDirsByParent[dirPath].orEmpty().map { childPath ->
                GitHubContentItem(
                    name = childPath.substringAfterLast('/'),
                    path = virtualPath(source, childPath),
                    type = "dir",
                    htmlUrl = source.githubUrl(childPath, tree = true),
                    repositoryId = source.id,
                    repositoryPath = childPath,
                    containsOnlyUnsupportedFiles = dirStates[childPath] == DIR_STATE_UNSUPPORTED
                )
            }
            grouped[virtualPath(source, dirPath)] =
                sortRepositoryItems((fileItems + dirItems).distinctBy { it.path })
            if (dirPath.isNotEmpty() && dirStates[dirPath] == DIR_STATE_UNSUPPORTED) {
                unsupportedDirectoryPaths += virtualPath(source, dirPath)
            }
        }

        return RepositoryIndexCache(
            grouped = grouped,
            unsupportedDirectoryPaths = unsupportedDirectoryPaths,
            documentFileCount = files.size
        )
    }

    private fun getDownloadRecords(): List<DownloadRecord> {
        val json = kv.decodeString(DOWNLOAD_RECORDS_KEY, "[]") ?: "[]"
        return try {
            val element = JsonParser.parseString(json)
            if (!element.isJsonArray) return emptyList()
            val array = element.asJsonArray
            if (array.size() == 0) {
                emptyList()
            } else if (array.firstOrNull()?.isJsonArray == true) {
                parseLegacyDownloadRecords(array)
            } else {
                val type = object : TypeToken<List<DownloadRecord>>() {}.type
                gson.fromJson<List<DownloadRecord>>(array, type).filter { it.path.isNotEmpty() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getDownloadRecord(path: String): DownloadRecord? {
        return getDownloadRecords().firstOrNull { it.path == path }
    }

    private fun parseLegacyDownloadRecords(array: JsonArray): List<DownloadRecord> {
        return array.mapNotNull { element ->
            val item = element.takeIf { it.isJsonArray }?.asJsonArray ?: return@mapNotNull null
            if (item.size() < 2) return@mapNotNull null
            val path = item[0].asString
            val localName = item[1].asString
            DownloadRecord(
                path = path,
                localName = localName,
                localPath = File(getLegacyDownloadDir(AppEnvironmentHolder.context()), localName).absolutePath
            )
        }
    }

    private fun saveDownloadRecord(record: DownloadRecord) {
        val files = getDownloadRecords().toMutableList()
        files.removeAll { it.path == record.path }
        files.add(record)
        kv.encode(DOWNLOAD_RECORDS_KEY, gson.toJson(files))
    }

    private fun removeDownloadRecord(path: String) {
        val files = getDownloadRecords().toMutableList()
        files.removeAll { it.path == path }
        kv.encode(DOWNLOAD_RECORDS_KEY, gson.toJson(files))
    }

    private fun saveContentCache(
        path: String,
        items: List<GitHubContentItem>,
        updateTime: Long = System.currentTimeMillis()
    ) {
        val key = contentCacheKey(path)
        kv.encode("$CONTENT_CACHE_PREFIX$key", gson.toJson(items))
        kv.encode("$CONTENT_CACHE_TIME_PREFIX$key", updateTime)
    }

    private fun createDownloadTarget(path: String, context: Context): DownloadTarget {
        val relativePath = safeRelativeFilePath(path)
        val parent = relativePath.substringBeforeLast('/', "")
        val displayName = relativePath.substringAfterLast('/').ifEmpty { "repository_file" }
        val relativeDirectory = listOf(
            Environment.DIRECTORY_DOWNLOADS,
            DOWNLOAD_RELATIVE_ROOT,
            parent
        )
            .filter { it.isNotEmpty() }
            .joinToString("/")
            .let { "$it/" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDirectory)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建下载文件")
            val displayPath = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "$DOWNLOAD_RELATIVE_ROOT/$relativePath"
            ).absolutePath
            DownloadTarget.MediaStoreTarget(context, uri, relativePath, displayPath)
        } else {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "$DOWNLOAD_RELATIVE_ROOT/$relativePath"
            )
            file.parentFile?.mkdirs()
            DownloadTarget.FileTarget(file, relativePath)
        }
    }

    private fun getLegacyDownloadDir(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(root, "repository")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeRelativeFilePath(path: String): String {
        val segments = path
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return segments.joinToString("/").ifEmpty { "repository_file" }
    }

    private fun sortRepositoryItems(items: List<GitHubContentItem>): List<GitHubContentItem> {
        val dirs = items.filter { it.type == "dir" }.sortedBy { it.name.lowercase() }
        val files = items.filter { it.type == "file" }.sortedBy { it.name.lowercase() }
        return dirs + files
    }

    private suspend fun getSelectedAccelerationSource(context: Context): RepositoryAccelerationSource {
        val selectedId = PreferencesManager(context.applicationContext)
            .repositoryAccelerationSource
            .first()
        return accelerationSources.firstOrNull { it.id == selectedId } ?: accelerationSources.first()
    }

    private fun matchesCurrentRepositorySources(items: List<GitHubContentItem>): Boolean {
        val cachedRoots = items.map { listOf(it.path, it.name, it.htmlUrl.orEmpty()) }
        val expectedRoots = repositoryRootItems().map { listOf(it.path, it.name, it.htmlUrl.orEmpty()) }
        return cachedRoots == expectedRoots
    }

    private fun virtualPath(source: RepositorySource, repositoryPath: String): String {
        val normalizedPath = normalizeRepositoryPath(repositoryPath)
        return listOf(source.id, normalizedPath)
            .filter { it.isNotEmpty() }
            .joinToString("/")
    }

    private fun resolveVirtualPath(path: String): ResolvedRepositoryPath? {
        val normalizedPath = normalizeRepositoryPath(path)
        if (normalizedPath.isEmpty()) return null
        val repositoryId = normalizedPath.substringBefore('/')
        val source = repositorySourceById[repositoryId] ?: return null
        val repositoryPath = normalizedPath.substringAfter('/', "")
        return ResolvedRepositoryPath(source, repositoryPath)
    }

    private fun repositoryRootUrl(): String {
        return "$GITHUB_HOST/Kaltsit-cell/AHU-CS-Repository"
    }

    private fun getUnsupportedDirectoryPaths(): Set<String> {
        val json = kv.decodeString(CONTENT_UNSUPPORTED_PATHS_KEY, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type).toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveUnsupportedDirectoryPaths(paths: Set<String>) {
        kv.encode(CONTENT_UNSUPPORTED_PATHS_KEY, gson.toJson(paths.sorted()))
    }

    private fun normalizeRepositoryPath(path: String): String {
        return path.replace('\\', '/').trim('/')
    }

    private fun encodePath(path: String): String {
        if (path.isEmpty()) return ""
        return path.split('/').joinToString("/") { Uri.encode(it) }
    }

    private fun contentCacheKey(path: String): String {
        return Base64.encodeToString(
            path.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE
        )
    }

    private data class DownloadRecord(
        val path: String,
        val localName: String,
        val uri: String? = null,
        val localPath: String? = null,
        val downloadTime: Long = 0L,
        val size: Long = 0L
    ) {
        fun toDownloadedFile(context: Context): DownloadedFile? {
            val uriValue = uri?.let { Uri.parse(it) }
            if (uriValue != null) {
                return DownloadedFile(
                    name = File(path).name,
                    path = path,
                    localPath = localPath ?: localName,
                    size = size.takeIf { it > 0L } ?: querySize(uriValue),
                    downloadTime = downloadTime,
                    uri = uri
                )
            }

            val file = File(localPath ?: File(getLegacyDownloadDir(context), localName).absolutePath)
            if (!file.exists()) return null
            return DownloadedFile(
                name = File(path).name,
                path = path,
                localPath = file.absolutePath,
                size = size.takeIf { it > 0L } ?: file.length(),
                downloadTime = if (downloadTime > 0) downloadTime else file.lastModified()
            )
        }

        fun delete(context: Context): Boolean {
            val uriValue = uri?.let { Uri.parse(it) }
            if (uriValue != null) {
                return context.contentResolver.delete(uriValue, null, null) > 0
            }

            val file = File(localPath ?: File(getLegacyDownloadDir(context), localName).absolutePath)
            return !file.exists() || file.delete()
        }

        private fun querySize(uri: Uri): Long {
            return runCatching {
                val context = AppEnvironmentHolder.context()
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    } else {
                        0L
                    }
                } ?: 0L
            }.getOrDefault(0L)
        }
    }

    private sealed class DownloadTarget(
        val relativePath: String,
        val displayPath: String,
        val uri: Uri?
    ) {
        abstract fun openOutputStream(): OutputStream
        open fun markCompleted() = Unit
        abstract fun delete()

        class MediaStoreTarget(
            private val context: Context,
            uri: Uri,
            relativePath: String,
            displayPath: String
        ) : DownloadTarget(relativePath, displayPath, uri) {
            override fun openOutputStream(): OutputStream {
                return context.contentResolver.openOutputStream(uri!!, "rwt")
                    ?: throw IllegalStateException("无法写入下载文件")
            }

            override fun markCompleted() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri!!, values, null, null)
                }
            }

            override fun delete() {
                context.contentResolver.delete(uri!!, null, null)
            }
        }

        class FileTarget(
            private val file: File,
            relativePath: String
        ) : DownloadTarget(relativePath, file.absolutePath, null) {
            private val tempFile = File(file.parentFile, "${file.name}.downloading")

            override fun openOutputStream(): OutputStream {
                tempFile.parentFile?.mkdirs()
                return FileOutputStream(tempFile)
            }

            override fun markCompleted() {
                if (file.exists()) {
                    file.delete()
                }
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            }

            override fun delete() {
                tempFile.delete()
            }
        }
    }

    private data class RepositorySource(
        val id: String,
        val title: String,
        val owner: String,
        val repo: String,
        val branch: String
    ) {
        fun rawUrl(repositoryPath: String): String {
            return "$RAW_HOST/$owner/$repo/$branch/${encodePath(repositoryPath)}"
        }

        fun cdnUrl(repositoryPath: String): String {
            return "$CDN_HOST/$owner/$repo@$branch/${encodePath(repositoryPath)}"
        }

        fun githubUrl(repositoryPath: String, tree: Boolean): String {
            val kind = if (tree) "tree" else "blob"
            val encodedPath = encodePath(repositoryPath)
            return if (encodedPath.isEmpty()) {
                "$GITHUB_HOST/$owner/$repo/tree/$branch"
            } else {
                "$GITHUB_HOST/$owner/$repo/$kind/$branch/$encodedPath"
            }
        }
    }

    private data class ResolvedRepositoryPath(
        val source: RepositorySource,
        val repositoryPath: String
    )

    private data class RepositoryIndexCache(
        val grouped: Map<String, List<GitHubContentItem>>,
        val unsupportedDirectoryPaths: Set<String>,
        val documentFileCount: Int
    )
}
