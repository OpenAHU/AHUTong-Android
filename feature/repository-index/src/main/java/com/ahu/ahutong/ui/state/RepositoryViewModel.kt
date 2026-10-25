package com.ahu.ahutong.ui.state

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.ahu.ahutong.core.common.UserNoticeHolder
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.data.repository.RepositoryIndex
import com.ahu.ahutong.data.repository.DownloadedFile
import com.ahu.ahutong.data.repository.GitHubContentItem
import com.ahu.ahutong.data.repository.RepositoryDirectorySummary
import com.ahu.ahutong.data.repository.RepositoryMarkdownDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Qualifier

/** IO 调度器限定符：生产注入 Dispatchers.IO，测试注入测试调度器，让用例完全确定。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

data class RepositoryUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoaded: Boolean = false,
    val items: List<GitHubContentItem> = emptyList(),
    val currentPath: String = "",
    val error: String? = null,
    val isShowingCachedContents: Boolean = false,
    val cacheUpdatedAt: Long? = null,
    val directorySummaries: Map<String, RepositoryDirectorySummary> = emptyMap()
)

data class RepositorySharedUiState(
    val downloadedPaths: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadingPath: String? = null,
    val isCacheWarming: Boolean = false,
    val cacheWarmUpCount: Int = 0,
    val indexDownloadBytes: Long? = null,
    val indexDownloadTotalBytes: Long? = null
)

data class RepositoryScrollPosition(
    val index: Int = 0,
    val offset: Int = 0
)

data class RepositoryMarkdownUiState(
    val isLoading: Boolean = false,
    val document: RepositoryMarkdownDocument? = null,
    val error: String? = null
)

data class RepositoryStoragePermissionRequest(val initialDirectoryUri: String?)

@HiltViewModel
class RepositoryViewModel @Inject constructor(
    private val repository: RepositoryIndex,
    private val fileAccess: RepositoryFileAccess,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private var loadRequestId = 0
    private val pathRequestIds = mutableMapOf<String, Int>()
    private val scrollPositions = mutableMapOf<String, RepositoryScrollPosition>()
    private var hasStartedWarmUp = false
    private var warmUpJob: Job? = null
    private var warmUpFailure: Exception? = null
    private val deletionMutex = Mutex()
    private sealed interface PendingStorageAction {
        data class Download(val item: GitHubContentItem) : PendingStorageAction
        data class Open(val file: DownloadedFile) : PendingStorageAction
    }
    private var pendingStorageAction: PendingStorageAction? = null
    private var storageGrantJob: Job? = null
    private val _storagePermissionRequest = MutableStateFlow<RepositoryStoragePermissionRequest?>(null)
    val storagePermissionRequest = _storagePermissionRequest.asStateFlow()

    fun cancelStoragePermission() {
        pendingStorageAction = null
        _storagePermissionRequest.value = null
        storageGrantJob?.cancel()
        storageGrantJob = null
    }

    fun onStoragePickerOpened() {
        _storagePermissionRequest.value = null
    }

    fun onDownloadDirectorySelected(uri: String?) {
        if (uri == null) {
            cancelStoragePermission()
            return
        }
        val action = pendingStorageAction ?: return
        if (storageGrantJob?.isActive == true) return
        _storagePermissionRequest.value = null
        storageGrantJob = viewModelScope.launch {
            try {
                withContext(ioDispatcher) { fileAccess.rememberDownloadDirectory(uri) }
                if (pendingStorageAction !== action) return@launch
                when (action) {
                    is PendingStorageAction.Download -> downloadFile(action.item, allowStoragePrompt = false)
                    is PendingStorageAction.Open -> {
                        val file = withContext(ioDispatcher) { fileAccess.recoverDownloadedFile(action.file) }
                        if (pendingStorageAction !== action) return@launch
                        if (file == null) {
                            _message.value = "所选目录中未找到原文件，请选择原下载目录或重新下载"
                        } else {
                            openDownloadedFile(file, allowStoragePrompt = false)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (pendingStorageAction === action) {
                    _message.value = "目录授权未完成：${error.message ?: "请重新选择可读写的文件夹"}"
                }
            } finally {
                if (pendingStorageAction === action) {
                    pendingStorageAction = null
                    storageGrantJob = null
                }
            }
        }
    }

    private suspend fun requestStoragePermission(action: PendingStorageAction, allowed: Boolean) {
        if (!allowed) {
            _message.value = "仍无法访问文件，请确认已选择原下载目录，或重新下载"
            return
        }
        // 指引、系统 picker、授权 IO 共用一个待恢复动作，后来的失败不能抢占它。
        if (pendingStorageAction != null) return
        pendingStorageAction = action
        val initialUri = withContext(ioDispatcher) { fileAccess.downloadDirectoryUri() }
        if (pendingStorageAction !== action) return
        _storagePermissionRequest.value = RepositoryStoragePermissionRequest(initialUri)
    }

    private val _directoryStates = MutableStateFlow<Map<String, RepositoryUiState>>(emptyMap())
    val directoryStates: StateFlow<Map<String, RepositoryUiState>> = _directoryStates.asStateFlow()

    private val _sharedState = MutableStateFlow(RepositorySharedUiState())
    val sharedState: StateFlow<RepositorySharedUiState> = _sharedState.asStateFlow()

    private val _markdownState = MutableStateFlow(RepositoryMarkdownUiState())
    val markdownState: StateFlow<RepositoryMarkdownUiState> = _markdownState.asStateFlow()

    /** 一次性提示（下载完成、文件已被删除……）。界面消费后清空。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    init {
        viewModelScope.launchSafe {
            val downloadedPaths = withContext(ioDispatcher) { refreshDownloadedSet() }
            _sharedState.value = _sharedState.value.copy(downloadedPaths = downloadedPaths)
        }
        // The app-scoped repository coalesces calls and keeps the completed result
        // until this process ends. Starting it here never blocks the directory.
        viewModelScope.launchSafe {
            repository.testAccelerationSources()
        }
    }

    fun getInitialDirectoryState(path: String): RepositoryUiState {
        return _directoryStates.value[path] ?: RepositoryUiState(
            currentPath = path,
            isLoading = true
        )
    }

    fun getDirectoryState(path: String): RepositoryUiState {
        return _directoryStates.value[path] ?: RepositoryUiState(
            currentPath = path,
            isLoading = true
        )
    }

    fun getSharedState(): RepositorySharedUiState = _sharedState.value

    fun ensureLoaded(path: String) {
        val state = _directoryStates.value[path]
        if (state == null || !state.isLoaded && state.error == null) {
            loadContents(path)
        }
    }

    fun loadContents(path: String = "", forceRefresh: Boolean = false) {
        if (forceRefresh) warmUpAllContentCaches(forceRefresh = true)
        val requestId = ++loadRequestId
        pathRequestIds[path] = requestId
        val startState = _directoryStates.value[path]

        setDirectoryState(
            path,
            (startState ?: RepositoryUiState(currentPath = path)).copy(
                isLoading = startState?.items.isNullOrEmpty(),
                isRefreshing = !startState?.items.isNullOrEmpty(),
                isLoaded = startState?.isLoaded == true,
                error = null
            )
        )

        viewModelScope.launch {
            try {
                val resolvedState = withContext(ioDispatcher) {
                    val cached = if (forceRefresh) null else repository.getCachedContents(path)
                    val readyCache = if (forceRefresh || cached == null && path.isNotBlank()) {
                        warmUpJob?.join()
                        warmUpFailure?.let { throw it }
                        repository.getCachedContents(path)
                    } else {
                        cached
                    }
                    if (readyCache != null) {
                        directoryStateFromCache(path, readyCache.items, readyCache.updateTime)
                    } else {
                        val items = repository.getContents(path, forceRefresh = false)
                        val sortedItems = sortDisplayItems(path, items)
                        RepositoryUiState(
                            isLoading = false,
                            isRefreshing = false,
                            isLoaded = true,
                            items = sortedItems,
                            currentPath = path,
                            isShowingCachedContents = false,
                            cacheUpdatedAt = System.currentTimeMillis(),
                            directorySummaries = repository.getDirectorySummaries(sortedItems)
                        )
                    }
                }
                if (pathRequestIds[path] != requestId) return@launch
                setDirectoryState(path, resolvedState)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (pathRequestIds[path] != requestId) return@launch
                val unexpected = UserNoticeHolder.reportUnexpected(e)
                val fallbackState = withContext(ioDispatcher) {
                    repository.getCachedContents(path)?.let { fallback ->
                        directoryStateFromCache(path, fallback.items, fallback.updateTime).copy(
                            error = null
                        )
                    }
                }
                if (fallbackState != null) {
                    setDirectoryState(path, fallbackState)
                } else {
                    setDirectoryState(
                        path,
                        (_directoryStates.value[path] ?: RepositoryUiState(currentPath = path)).copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (unexpected) "加载失败，请查看错误详情" else "加载失败: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun warmUpAllContentCaches(forceRefresh: Boolean = false) {
        if (warmUpJob?.isActive == true) return
        if (hasStartedWarmUp && !forceRefresh) return
        hasStartedWarmUp = true
        warmUpFailure = null
        _sharedState.value = _sharedState.value.copy(
            isCacheWarming = true,
            cacheWarmUpCount = 0,
            indexDownloadBytes = null,
            indexDownloadTotalBytes = null
        )
        warmUpJob = viewModelScope.launch {
            try {
                repository.warmUpAllContentCaches(
                    forceRefresh = forceRefresh,
                    onProgress = { fetchedCount ->
                        _sharedState.value = _sharedState.value.copy(
                            isCacheWarming = true,
                            cacheWarmUpCount = fetchedCount,
                            indexDownloadBytes = null,
                            indexDownloadTotalBytes = null
                        )
                    },
                    onDownloadProgress = { downloaded, total ->
                        _sharedState.value = _sharedState.value.copy(
                            indexDownloadBytes = downloaded,
                            indexDownloadTotalBytes = total.takeIf { it > 0L }
                        )
                    }
                )
                val states = withContext(ioDispatcher) {
                    _directoryStates.value.toMutableMap().also { currentStates ->
                        currentStates.keys.toList().forEach { path ->
                            repository.getCachedContents(path)?.let { cached ->
                                currentStates[path] = directoryStateFromCache(
                                    path,
                                    cached.items,
                                    cached.updateTime
                                )
                            }
                        }
                    }
                }
                _directoryStates.value = states
                _sharedState.value = _sharedState.value.copy(
                    isCacheWarming = false,
                    cacheWarmUpCount = 0,
                    indexDownloadBytes = null,
                    indexDownloadTotalBytes = null
                )
            } catch (cancelled: CancellationException) {
                hasStartedWarmUp = false
                _sharedState.value = _sharedState.value.copy(
                    isCacheWarming = false,
                    indexDownloadBytes = null,
                    indexDownloadTotalBytes = null
                )
                throw cancelled
            } catch (error: Exception) {
                UserNoticeHolder.reportUnexpected(error)
                warmUpFailure = error
                hasStartedWarmUp = false
                _sharedState.value = _sharedState.value.copy(
                    isCacheWarming = false,
                    cacheWarmUpCount = 0,
                    indexDownloadBytes = null,
                    indexDownloadTotalBytes = null
                )
            }
        }
    }

    fun refreshDirectory(path: String) {
        loadContents(path, forceRefresh = true)
    }

    fun saveScrollPosition(path: String, index: Int, offset: Int) {
        scrollPositions[path] = RepositoryScrollPosition(index, offset)
    }

    fun getScrollPosition(path: String): RepositoryScrollPosition {
        return scrollPositions[path] ?: RepositoryScrollPosition()
    }

    fun downloadFile(item: GitHubContentItem) = downloadFile(item, allowStoragePrompt = true)

    private fun downloadFile(item: GitHubContentItem, allowStoragePrompt: Boolean) {
        viewModelScope.launch {
            val path = item.path
            _sharedState.value = _sharedState.value.copy(
                downloadingPath = path,
                downloadProgress = _sharedState.value.downloadProgress + (path to 0f)
            )
            try {
                val file = repository.downloadFile(path) { progress ->
                    _sharedState.value = _sharedState.value.copy(
                        downloadProgress = _sharedState.value.downloadProgress + (path to progress)
                    )
                }
                if (file != null) {
                    val downloads = withContext(ioDispatcher) { refreshDownloadedSet() }
                    _sharedState.value = _sharedState.value.copy(
                        downloadingPath = null,
                        downloadedPaths = downloads,
                        downloadProgress = _sharedState.value.downloadProgress - path
                    )
                    _message.value = "下载完成: ${item.name}"
                } else {
                    _sharedState.value = _sharedState.value.copy(
                        downloadingPath = null,
                        downloadProgress = _sharedState.value.downloadProgress - path
                    )
                    setPathError(item.parentPath(), buildDownloadErrorMessage(item.name))
                }
            } catch (cancelled: CancellationException) {
                _sharedState.value = _sharedState.value.copy(
                    downloadingPath = null,
                    downloadProgress = _sharedState.value.downloadProgress - path
                )
                throw cancelled
            } catch (_: SecurityException) {
                _sharedState.value = _sharedState.value.copy(
                    downloadingPath = null,
                    downloadProgress = _sharedState.value.downloadProgress - path
                )
                requestStoragePermission(PendingStorageAction.Download(item), allowStoragePrompt)
            } catch (e: Exception) {
                _sharedState.value = _sharedState.value.copy(
                    downloadingPath = null,
                    downloadProgress = _sharedState.value.downloadProgress - path
                )
                val unexpected = UserNoticeHolder.reportUnexpected(e)
                setPathError(item.parentPath(), if (unexpected) "下载失败，请查看错误详情" else buildDownloadErrorMessage(e.message))
            }
        }
    }

    fun openFile(item: GitHubContentItem) {
        if (isMarkdownFile(item.name)) {
            loadMarkdown(item.path)
            return
        }
        viewModelScope.launch {
            val file = withContext(ioDispatcher) { repository.getDownloadedFile(item.path) }
            if (file != null) openDownloadedFile(file)
            else _message.value = "文件不存在，请先下载"
        }
    }

    fun deleteFile(path: String) {
        deleteFiles(listOf(path))
    }

    internal fun deleteFiles(
        paths: Collection<String>,
        onComplete: (Result<RepositoryDeletionResult>) -> Unit = {}
    ) {
        viewModelScope.launch {
            deletionMutex.withLock {
                val result = try {
                    Result.success(withContext(ioDispatcher) {
                        deleteDownloadedFiles(
                            paths = paths,
                            delete = { repository.deleteFile(it) },
                            listFiles = { repository.getDownloadedFiles() }
                        )
                    })
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    UserNoticeHolder.reportUnexpected(exception)
                    Result.failure(exception)
                }
                result.onSuccess { deletion ->
                    _sharedState.value = _sharedState.value.copy(
                        downloadedPaths = deletion.remainingFiles.mapTo(mutableSetOf()) { it.path }
                    )
                }
                onComplete(result)
            }
        }
    }

    fun getRawUrl(path: String): String = repository.getRawUrl(path)

    fun getGitHubUrl(path: String): String = repository.getGitHubUrl(path)

    fun getRepositoryTitle(repoId: String): String = repository.getRepositoryTitle(repoId)

    fun formatDisplayPath(path: String): String = repository.formatDisplayPath(path)

    fun shouldShowUnsupportedDirectoryMessage(path: String): Boolean {
        return repository.shouldShowUnsupportedDirectoryMessage(path)
    }

    fun loadMarkdown(path: String) {
        _markdownState.value = RepositoryMarkdownUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val document = repository.getMarkdownDocument(path)
                _markdownState.value = RepositoryMarkdownUiState(document = document)
            } catch (cancelled: CancellationException) {
                _markdownState.value = RepositoryMarkdownUiState()
                throw cancelled
            } catch (e: Exception) {
                val unexpected = UserNoticeHolder.reportUnexpected(e)
                _markdownState.value = RepositoryMarkdownUiState(
                    error = if (unexpected) "Markdown 加载失败，请查看错误详情" else "Markdown 加载失败: ${e.message}"
                )
            }
        }
    }

    fun clearMarkdown() {
        _markdownState.value = RepositoryMarkdownUiState()
    }

    private fun loadDownloadedMarkdown(file: DownloadedFile, allowStoragePrompt: Boolean) {
        _markdownState.value = RepositoryMarkdownUiState(isLoading = true)
        viewModelScope.launch {
            when (val outcome = withContext(ioDispatcher) { fileAccess.read(file) }) {
                is FileReadOutcome.Text -> _markdownState.value = RepositoryMarkdownUiState(
                    document = RepositoryMarkdownDocument(
                        title = file.name,
                        path = file.path,
                        content = outcome.content
                    )
                )

                FileReadOutcome.Missing -> {
                    deleteFile(file.path)
                    _markdownState.value = RepositoryMarkdownUiState(
                        error = "Markdown 加载失败: 文件已被删除"
                    )
                }

                FileReadOutcome.PermissionRequired -> {
                    _markdownState.value = RepositoryMarkdownUiState()
                    requestStoragePermission(PendingStorageAction.Open(file), allowStoragePrompt)
                }

                is FileReadOutcome.Failed -> _markdownState.value = RepositoryMarkdownUiState(
                    error = "Markdown 加载失败: ${outcome.message}"
                )
            }
        }
    }

    fun getDownloadedFiles(): List<DownloadedFile> {
        return repository.getDownloadedFiles()
    }

    fun getLocalFile(path: String): File? {
        return repository.getLocalFile(path)
    }

    fun openDownloadedFile(file: DownloadedFile) = openDownloadedFile(file, allowStoragePrompt = true)

    private fun openDownloadedFile(file: DownloadedFile, allowStoragePrompt: Boolean) {
        if (isMarkdownFile(file.name)) {
            loadDownloadedMarkdown(file, allowStoragePrompt)
            return
        }
        viewModelScope.launch {
            when (val outcome = withContext(ioDispatcher) { fileAccess.open(file) }) {
                FileOpenOutcome.Opened -> Unit
                FileOpenOutcome.Missing -> {
                    deleteFile(file.path)
                    _message.value = "文件已被删除"
                }
                FileOpenOutcome.PermissionRequired ->
                    requestStoragePermission(PendingStorageAction.Open(file), allowStoragePrompt)
                FileOpenOutcome.NoViewerApp -> _message.value = "没有找到可打开此文件的软件"
                is FileOpenOutcome.Failed -> _message.value = "打开失败: ${outcome.message}"
            }
        }
    }

    fun clearError(path: String) {
        _directoryStates.value[path]?.let { state ->
            setDirectoryState(path, state.copy(error = null))
        }
    }

    private fun directoryStateFromCache(
        path: String,
        items: List<GitHubContentItem>,
        updateTime: Long
    ): RepositoryUiState {
        val sortedItems = sortDisplayItems(path, items)
        return RepositoryUiState(
            isLoading = false,
            isRefreshing = false,
            isLoaded = true,
            items = sortedItems,
            currentPath = path,
            isShowingCachedContents = true,
            cacheUpdatedAt = updateTime.takeIf { it > 0L },
            directorySummaries = repository.getDirectorySummaries(sortedItems)
        )
    }

    private fun setDirectoryState(path: String, state: RepositoryUiState) {
        _directoryStates.value = _directoryStates.value + (path to state.copy(currentPath = path))
    }

    private fun setPathError(path: String, message: String) {
        setDirectoryState(
            path,
            (_directoryStates.value[path] ?: RepositoryUiState(
                currentPath = path
            )).copy(error = message)
        )
    }

    private fun GitHubContentItem.parentPath(): String = path.substringBeforeLast('/', "")

    private fun refreshDownloadedSet(): Set<String> {
        val files = repository.getDownloadedFiles()
        return files.map { it.path }.toSet()
    }

    private fun buildDownloadErrorMessage(detail: String?): String {
        val suffix = "，可前往学习资料设置更换下载源后重试"
        val normalizedDetail = detail?.takeIf { it.isNotBlank() } ?: "未知原因"
        return "下载失败: $normalizedDetail$suffix"
    }

    private fun sortDisplayItems(path: String, items: List<GitHubContentItem>): List<GitHubContentItem> {
        if (path.isBlank()) {
            return items.sortedWith(
                compareBy<GitHubContentItem> { if (it.type == "dir") 0 else 1 }
                    .thenBy { repository.getRepositoryOrder(it.path) }
                    .thenBy { it.name.lowercase() }
            )
        }
        val dirs = items.filter { it.type == "dir" }.sortedBy { it.name.lowercase() }
        val files = items.filter { it.type == "file" }.sortedBy { it.name.lowercase() }
        return dirs + files
    }

    companion object {
        fun isMarkdownFile(name: String): Boolean {
            return name.lowercase().endsWith(".md")
        }

        fun getMimeType(fileName: String): String {
            val lower = fileName.lowercase()
            return when {
                lower.endsWith(".pdf") -> "application/pdf"
                lower.endsWith(".doc") -> "application/msword"
                lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                lower.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
                lower.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                lower.endsWith(".xls") -> "application/vnd.ms-excel"
                lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                lower.endsWith(".txt") -> "text/plain"
                lower.endsWith(".md") -> "text/plain"
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                lower.endsWith(".gif") -> "image/gif"
                lower.endsWith(".webp") -> "image/webp"
                lower.endsWith(".bmp") -> "image/bmp"
                lower.endsWith(".svg") -> "image/svg+xml"
                lower.endsWith(".zip") -> "application/zip"
                lower.endsWith(".rar") -> "application/x-rar-compressed"
                lower.endsWith(".7z") -> "application/x-7z-compressed"
                lower.endsWith(".apk") -> "application/vnd.android.package-archive"
                else -> "*/*"
            }
        }

        fun getFileTypeIcon(name: String): String {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".pdf") -> "PDF"
                lower.endsWith(".doc") || lower.endsWith(".docx") -> "DOC"
                lower.endsWith(".ppt") || lower.endsWith(".pptx") -> "PPT"
                lower.endsWith(".xls") || lower.endsWith(".xlsx") -> "XLS"
                lower.endsWith(".txt") -> "TXT"
                lower.endsWith(".md") -> "MD"
                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") -> "IMG"
                lower.endsWith(".svg") -> "SVG"
                lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") -> "ZIP"
                lower.endsWith(".apk") -> "APK"
                else -> "?"
            }
        }
    }
}
