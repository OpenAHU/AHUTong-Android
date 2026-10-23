package com.ahu.ahutong.testing

import com.ahu.ahutong.data.repository.CachedRepositoryContents
import com.ahu.ahutong.data.repository.DownloadedFile
import com.ahu.ahutong.data.repository.GitHubContentItem
import com.ahu.ahutong.data.repository.RepositoryAccelerationSource
import com.ahu.ahutong.data.repository.RepositoryDirectorySummary
import com.ahu.ahutong.data.repository.RepositoryIndex
import com.ahu.ahutong.data.repository.RepositoryMarkdownDocument
import com.ahu.ahutong.data.repository.RepositorySourceHealth
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * [RepositoryIndex] 的 fake：提供可控的加速源检测结果，其余一律空实现。
 *
 * 空实现而不是抛异常：这样将来别的用例偶然碰到某个方法时，得到的是"没有内容"
 * 而不是一个和被测行为无关的崩溃。
 */
class FakeRepositoryIndex(
    override val accelerationSources: List<RepositoryAccelerationSource> = listOf(
        RepositoryAccelerationSource(id = "jsdelivr", name = "jsDelivr", description = "默认"),
        RepositoryAccelerationSource(id = "ghfast", name = "GHFast", description = "加速"),
        RepositoryAccelerationSource(id = "gh-proxy", name = "gh-proxy", description = "加速"),
        RepositoryAccelerationSource(id = "direct", name = "GitHub 直连", description = "直连"),
        RepositoryAccelerationSource(
            id = "muxyang-fallback",
            name = "备用源",
            description = "其他源均不可用时启用",
            isFallback = true
        )
    )
) : RepositoryIndex {

    override val accelerationSourceHealth =
        MutableStateFlow<Map<String, RepositorySourceHealth>>(emptyMap())
    val sourceTestRequests = mutableListOf<Boolean>()
    var onSourceTest: suspend (Boolean) -> Unit = {}

    override suspend fun testAccelerationSources(force: Boolean) {
        sourceTestRequests += force
        onSourceTest(force)
    }

    override suspend fun getContents(
        path: String,
        forceRefresh: Boolean
    ): List<GitHubContentItem> = emptyList()

    override fun getCachedContents(path: String): CachedRepositoryContents? = null

    override suspend fun warmUpAllContentCaches(
        forceRefresh: Boolean,
        onProgress: ((Int) -> Unit)?,
        onDownloadProgress: ((Long, Long) -> Unit)?
    ): Long = 0L

    override fun getDirectorySummaries(
        items: List<GitHubContentItem>
    ): Map<String, RepositoryDirectorySummary> = emptyMap()

    override fun shouldShowUnsupportedDirectoryMessage(path: String): Boolean = false

    override suspend fun getMarkdownDocument(path: String): RepositoryMarkdownDocument =
        RepositoryMarkdownDocument(title = path, path = path, content = "")

    override fun getRawUrl(path: String): String = path

    override fun getGitHubUrl(path: String): String = path

    override fun getRepositoryTitle(repoId: String): String = repoId

    override fun getRepositoryOrder(path: String): Int = 0

    override fun formatDisplayPath(path: String): String = path

    override suspend fun downloadFile(path: String, onProgress: (Float) -> Unit): DownloadedFile? =
        null

    override fun getDownloadedFiles(): List<DownloadedFile> = emptyList()

    override fun getDownloadedFile(path: String): DownloadedFile? = null

    override fun getLocalFile(path: String): File? = null

    override fun deleteFile(path: String): Boolean = false
}

