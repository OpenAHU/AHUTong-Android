package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.UserNotice
import com.ahu.ahutong.core.common.UserNoticeHolder
import com.ahu.ahutong.data.repository.CachedRepositoryContents
import com.ahu.ahutong.data.repository.DownloadedFile
import com.ahu.ahutong.data.repository.GitHubContentItem
import com.ahu.ahutong.data.repository.RepositoryAccelerationSource
import com.ahu.ahutong.data.repository.RepositoryDirectorySummary
import com.ahu.ahutong.data.repository.RepositoryIndex
import com.ahu.ahutong.data.repository.RepositoryMarkdownDocument
import com.ahu.ahutong.data.repository.RepositorySourceHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * feature 的 ViewModel 契约（P3 的交付要求：ViewModel 只依赖接口 + 自带 fake 的 JVM 测试）。
 *
 * 这些用例在接缝建立之前写不出来：那时 ViewModel 是 AndroidViewModel，手里攥着全局 RepositoryManager，
 * 在 JVM 上根本无法构造。现在它只认识 RepositoryIndex 与 RepositoryFileAccess 两个接口。
 */
class RepositoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepositoryIndex
    private lateinit var fileAccess: FakeRepositoryFileAccess

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepositoryIndex()
        fileAccess = FakeRepositoryFileAccess()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = RepositoryViewModel(repository, fileAccess, dispatcher)

    @Test
    fun `entering study materials starts source checks without blocking the directory`() = runTest(dispatcher) {
        val probeGate = CompletableDeferred<Unit>()
        repository.sourceProbeGate = probeGate
        repository.cached[""] = CachedRepositoryContents(listOf(file("a.md")), updateTime = 123L)
        val vm = viewModel()
        vm.ensureLoaded("")
        runCurrent()
        assertEquals(1, repository.sourceProbeCalls)
        assertTrue(vm.directoryStates.value.getValue("").isLoaded)
        val state = vm.directoryStates.value.getValue("")
        assertEquals(listOf("a.md"), state.items.map { it.name })
        assertTrue(state.isShowingCachedContents)
        assertEquals(123L, state.cacheUpdatedAt)
        assertEquals(0, repository.getContentsCalls)
        probeGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `a refresh reads through the port and keeps the derived summaries`() = runTest(dispatcher) {
        repository.contents["repo"] = listOf(dir("z-dir"), file("a.md"), dir("a-dir"))
        val vm = viewModel()

        vm.loadContents("repo", forceRefresh = true)
        advanceUntilIdle()

        val state = vm.directoryStates.value.getValue("repo")
        assertEquals(1, repository.getContentsCalls)
        // 目录在前、同类按名字排序 —— 排序规则与迁移前一致。
        assertEquals(listOf("a-dir", "z-dir", "a.md"), state.items.map { it.name })
        assertEquals(setOf("a-dir", "z-dir", "a.md"), state.directorySummaries.keys)
        assertNull(state.error)
    }

    @Test
    fun `a failed fetch falls back to the cache instead of showing an error`() = runTest(dispatcher) {
        repository.cached["repo"] = CachedRepositoryContents(listOf(file("cached.md")), updateTime = 7L)
        repository.failNextWarmUp = true
        val vm = viewModel()

        vm.loadContents("repo", forceRefresh = true)
        advanceUntilIdle()

        val state = vm.directoryStates.value.getValue("repo")
        assertEquals(listOf("cached.md"), state.items.map { it.name })
        assertNull(state.error)
    }

    @Test
    fun `a failed fetch without a cache surfaces the error`() = runTest(dispatcher) {
        repository.failNextGetContents = true
        val vm = viewModel()

        vm.loadContents("repo", forceRefresh = true)
        advanceUntilIdle()

        val state = vm.directoryStates.value.getValue("repo")
        assertTrue(state.error!!.startsWith("加载失败"))
    }

    @Test
    fun `a child directory waits for the shared index download`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.warmUpGate = gate
        repository.downloadProgress = 512L to 1_024L
        val vm = viewModel()

        vm.warmUpAllContentCaches()
        vm.loadContents("repo")
        runCurrent()
        assertEquals(0, repository.getContentsCalls)
        assertEquals(512L, vm.sharedState.value.indexDownloadBytes)
        assertEquals(1_024L, vm.sharedState.value.indexDownloadTotalBytes)

        repository.cached["repo"] = CachedRepositoryContents(listOf(file("ready.md")), 9L)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("ready.md"), vm.directoryStates.value.getValue("repo").items.map { it.name })
        assertEquals(0, repository.getContentsCalls)
    }

    @Test
    fun `manual refresh of a child directory reports the same index download`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.warmUpGate = gate
        repository.downloadProgress = 256L to 1_024L
        val vm = viewModel()

        vm.loadContents("repo", forceRefresh = true)
        runCurrent()
        assertEquals(256L, vm.sharedState.value.indexDownloadBytes)
        assertEquals(0, repository.getContentsCalls)

        repository.cached["repo"] = CachedRepositoryContents(listOf(file("new.md")), 11L)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("new.md"), vm.directoryStates.value.getValue("repo").items.map { it.name })
        assertEquals(0, repository.getContentsCalls)
    }

    @Test
    fun `an unchanged server index keeps the displayed cache time`() = runTest(dispatcher) {
        repository.cached["repo"] = CachedRepositoryContents(listOf(file("a.md")), 7L)
        val vm = viewModel()
        vm.loadContents("repo")
        advanceUntilIdle()

        vm.warmUpAllContentCaches(forceRefresh = true)
        advanceUntilIdle()

        assertEquals(7L, vm.directoryStates.value.getValue("repo").cacheUpdatedAt)
    }

    @Test
    fun `a finished download updates the set and reports through the message channel`() = runTest(dispatcher) {
        val downloaded = DownloadedFile(name = "a.md", path = "repo/a.md", localPath = "/tmp/a.md")
        repository.downloadResult = downloaded
        repository.downloaded += downloaded
        val vm = viewModel()

        vm.downloadFile(file("a.md", path = "repo/a.md"))
        advanceUntilIdle()

        assertEquals(setOf("repo/a.md"), vm.sharedState.value.downloadedPaths)
        assertEquals("下载完成: a.md", vm.message.value)
        assertNull(vm.sharedState.value.downloadingPath)
    }

    @Test
    fun `an unexpected download failure preserves the original throwable for the dialog`() = runTest(dispatcher) {
        val failure = IllegalStateException("synthetic failure", IllegalArgumentException("nested cause"))
        repository.downloadFailure = failure
        val reported = mutableListOf<Throwable>()
        UserNoticeHolder.install(object : UserNotice {
            override fun showFailure(error: Throwable) = Unit
            override fun reportUnexpected(error: Throwable): Boolean {
                reported += error
                return true
            }
        })
        try {
            val vm = viewModel()
            vm.downloadFile(file("a.pdf", path = "repo/a.pdf"))
            advanceUntilIdle()
            assertSame(failure, reported.single())
            assertEquals("下载失败，请查看错误详情", vm.directoryStates.value.getValue("repo").error)
            assertNull(vm.sharedState.value.downloadingPath)
        } finally {
            UserNoticeHolder.install(object : UserNotice { override fun showFailure(error: Throwable) = Unit })
        }
    }

    @Test
    fun `cancelled download clears progress without reporting an unexpected failure`() = runTest(dispatcher) {
        repository.downloadFailure = kotlinx.coroutines.CancellationException("cancelled")
        val reported = mutableListOf<Throwable>()
        UserNoticeHolder.install(object : UserNotice {
            override fun showFailure(error: Throwable) { reported += error }
            override fun reportUnexpected(error: Throwable): Boolean { reported += error; return true }
        })
        try {
            val vm = viewModel()
            vm.downloadFile(file("a.pdf", path = "repo/a.pdf"))
            advanceUntilIdle()
            assertTrue(reported.isEmpty())
            assertNull(vm.sharedState.value.downloadingPath)
            assertTrue(vm.sharedState.value.downloadProgress.isEmpty())
        } finally {
            UserNoticeHolder.install(object : UserNotice { override fun showFailure(error: Throwable) = Unit })
        }
    }

    @Test
    fun `opening a file that was never downloaded asks the user to download first`() = runTest(dispatcher) {
        // 非 markdown：markdown 会先走"取文档内容"的分支。
        val vm = viewModel()

        vm.openFile(file("a.pdf", path = "repo/a.pdf"))
        advanceUntilIdle()

        assertEquals("文件不存在，请先下载", vm.message.value)
    }

    @Test
    fun `a markdown file that disappeared is deleted and reported`() = runTest(dispatcher) {
        val downloaded = DownloadedFile(name = "a.md", path = "repo/a.md", localPath = "/tmp/a.md")
        repository.downloaded += downloaded
        fileAccess.readOutcome = FileReadOutcome.Missing
        val vm = viewModel()

        vm.openDownloadedFile(downloaded)
        advanceUntilIdle()

        assertTrue(vm.markdownState.value.error!!.contains("文件已被删除"))
        assertTrue(repository.downloaded.isEmpty())
    }

    @Test
    fun `cancelled directory selection keeps the failed download stopped`() = runTest(dispatcher) {
        repository.downloadFailure = SecurityException("directory grant revoked")
        val vm = viewModel()
        vm.downloadFile(file("a.pdf"))
        advanceUntilIdle()
        assertNotNull(vm.storagePermissionRequest.value)
        assertNull(vm.sharedState.value.downloadingPath)
        assertTrue(vm.sharedState.value.downloadProgress.isEmpty())

        vm.onStoragePickerOpened()
        vm.onDownloadDirectorySelected(null)
        vm.onDownloadDirectorySelected("content://documents/tree/selected")
        advanceUntilIdle()

        assertEquals(1, repository.downloadCalls)
        assertTrue(fileAccess.rememberedDirectories.isEmpty())
        assertNull(vm.storagePermissionRequest.value)
    }

    @Test
    fun `a valid directory grant resumes the pending download only once`() = runTest(dispatcher) {
        repository.downloadFailure = SecurityException("directory grant revoked")
        val vm = viewModel()
        vm.downloadFile(file("a.pdf"))
        advanceUntilIdle()
        fileAccess.rememberDirectory = {
            repository.downloadFailure = null
            repository.downloadResult = DownloadedFile("a.pdf", "a.pdf", "/downloads/a.pdf")
        }

        vm.onDownloadDirectorySelected("content://documents/tree/selected")
        vm.onDownloadDirectorySelected("content://documents/tree/selected")
        advanceUntilIdle()

        assertEquals(2, repository.downloadCalls)
        assertEquals(1, fileAccess.rememberedDirectories.size)
        assertEquals("下载完成: a.pdf", vm.message.value)
        assertNull(vm.storagePermissionRequest.value)
    }

    @Test
    fun `a second failed download cannot replace the action while its directory picker is open`() = runTest(dispatcher) {
        repository.downloadFailure = SecurityException("directory grant revoked")
        val vm = viewModel()
        vm.downloadFile(file("first.pdf"))
        advanceUntilIdle()
        vm.onStoragePickerOpened()

        vm.downloadFile(file("second.pdf"))
        advanceUntilIdle()
        assertNull(vm.storagePermissionRequest.value)
        fileAccess.rememberDirectory = {
            repository.downloadFailure = null
            repository.downloadResult = DownloadedFile("first.pdf", "first.pdf", "/downloads/first.pdf")
        }

        vm.onDownloadDirectorySelected("content://documents/tree/selected")
        advanceUntilIdle()

        assertEquals(listOf("first.pdf", "second.pdf", "first.pdf"), repository.downloadRequests)
        assertEquals(1, fileAccess.rememberedDirectories.size)
        assertNull(vm.storagePermissionRequest.value)
    }

    @Test
    fun `a still inaccessible file keeps its record without reopening the directory picker`() = runTest(dispatcher) {
        val downloaded = DownloadedFile("a.pdf", "repo/a.pdf", "/downloads/a.pdf")
        repository.downloaded += downloaded
        fileAccess.openOutcome = FileOpenOutcome.PermissionRequired
        val vm = viewModel()
        vm.openDownloadedFile(downloaded)
        advanceUntilIdle()
        assertNotNull(vm.storagePermissionRequest.value)

        vm.onDownloadDirectorySelected("content://documents/tree/selected")
        advanceUntilIdle()

        assertEquals(2, fileAccess.openCalls)
        assertEquals(listOf(downloaded), repository.downloaded)
        assertNull(vm.storagePermissionRequest.value)
        assertTrue(vm.message.value.orEmpty().contains("重新下载"))
    }

    @Test
    fun `lost markdown permission preserves the download record and cancellation stops recovery`() = runTest(dispatcher) {
        val downloaded = DownloadedFile("a.md", "repo/a.md", "/downloads/a.md")
        repository.downloaded += downloaded
        fileAccess.readOutcome = FileReadOutcome.PermissionRequired
        val vm = viewModel()
        vm.openDownloadedFile(downloaded)
        advanceUntilIdle()
        assertNotNull(vm.storagePermissionRequest.value)
        assertTrue(!vm.markdownState.value.isLoading)

        vm.cancelStoragePermission()
        advanceUntilIdle()

        assertEquals(1, fileAccess.readCalls)
        assertEquals(listOf(downloaded), repository.downloaded)
        assertNull(vm.storagePermissionRequest.value)
    }

    private fun file(name: String, path: String = name) =
        GitHubContentItem(name = name, path = path, type = "file")

    private fun dir(name: String) = GitHubContentItem(name = name, path = name, type = "dir")

    private class FakeRepositoryIndex : RepositoryIndex {
        val cached = mutableMapOf<String, CachedRepositoryContents>()
        val contents = mutableMapOf<String, List<GitHubContentItem>>()
        val downloaded = mutableListOf<DownloadedFile>()
        var getContentsCalls = 0
        var failNextGetContents = false
        var failNextWarmUp = false
        var downloadResult: DownloadedFile? = null
        var downloadFailure: Throwable? = null
        val downloadRequests = mutableListOf<String>()
        val downloadCalls: Int get() = downloadRequests.size
        var sourceProbeGate: CompletableDeferred<Unit>? = null
        var sourceProbeCalls = 0
        var warmUpGate: CompletableDeferred<Unit>? = null
        var downloadProgress: Pair<Long, Long>? = null

        override val accelerationSources: List<RepositoryAccelerationSource> = emptyList()
        override val accelerationSourceHealth = MutableStateFlow<Map<String, RepositorySourceHealth>>(emptyMap())
        override suspend fun testAccelerationSources(force: Boolean) {
            sourceProbeCalls++
            sourceProbeGate?.await()
        }

        override suspend fun getContents(path: String, forceRefresh: Boolean): List<GitHubContentItem> {
            getContentsCalls++
            if (failNextGetContents) {
                failNextGetContents = false
                throw IllegalStateException("boom")
            }
            return contents[path].orEmpty()
        }

        override fun getCachedContents(path: String): CachedRepositoryContents? = cached[path]

        override suspend fun warmUpAllContentCaches(
            forceRefresh: Boolean,
            onProgress: ((Int) -> Unit)?,
            onDownloadProgress: ((Long, Long) -> Unit)?
        ): Long {
            if (failNextWarmUp) {
                failNextWarmUp = false
                throw IllegalStateException("boom")
            }
            downloadProgress?.let { (downloaded, total) ->
                onDownloadProgress?.invoke(downloaded, total)
            }
            warmUpGate?.await()
            return 0L
        }

        override fun getDirectorySummaries(
            items: List<GitHubContentItem>
        ): Map<String, RepositoryDirectorySummary> =
            items.associate { it.path to RepositoryDirectorySummary() }

        override fun shouldShowUnsupportedDirectoryMessage(path: String): Boolean = false

        override suspend fun getMarkdownDocument(path: String): RepositoryMarkdownDocument =
            RepositoryMarkdownDocument(title = path, path = path, content = path)

        override fun getRawUrl(path: String): String = path

        override fun getGitHubUrl(path: String): String = path

        override fun getRepositoryTitle(repoId: String): String = repoId

        override fun getRepositoryOrder(path: String): Int = 0

        override fun formatDisplayPath(path: String): String = path

        override suspend fun downloadFile(
            path: String,
            onProgress: (Float) -> Unit
        ): DownloadedFile? {
            downloadRequests += path
            downloadFailure?.let { throw it }
            return downloadResult
        }

        override fun getDownloadedFiles(): List<DownloadedFile> = downloaded.toList()

        override fun getDownloadedFile(path: String): DownloadedFile? =
            downloaded.firstOrNull { it.path == path }

        override fun getLocalFile(path: String): File? = null

        override fun deleteFile(path: String): Boolean {
            downloaded.removeAll { it.path == path }
            return true
        }
    }

    private class FakeRepositoryFileAccess : RepositoryFileAccess {
        var readOutcome: FileReadOutcome = FileReadOutcome.Text("内容")
        var openOutcome: FileOpenOutcome = FileOpenOutcome.Opened
        var readCalls = 0
        var openCalls = 0
        var rememberDirectory: () -> Unit = {}
        val rememberedDirectories = mutableListOf<String>()

        override fun rememberDownloadDirectory(uri: String) {
            rememberedDirectories += uri
            rememberDirectory()
        }

        override fun read(file: DownloadedFile): FileReadOutcome {
            readCalls++
            return readOutcome
        }

        override fun open(file: DownloadedFile): FileOpenOutcome {
            openCalls++
            return openOutcome
        }
    }
}
