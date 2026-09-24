package com.ahu.ahutong.testing

import com.ahu.ahutong.data.repository.CachedRepositoryContents
import com.ahu.ahutong.data.repository.DownloadedFile
import com.ahu.ahutong.data.repository.GitHubContentItem
import com.ahu.ahutong.data.repository.RepositoryAccelerationSource
import com.ahu.ahutong.data.repository.RepositoryDirectorySummary
import com.ahu.ahutong.data.repository.RepositoryIndex
import com.ahu.ahutong.data.repository.RepositoryMarkdownDocument
import java.io.File

/**
 * [RepositoryIndex] 的 fake：设置页只看加速源列表，其余一律空实现。
 *
 * 空实现而不是抛异常：这样将来别的用例偶然碰到某个方法时，得到的是"没有内容"
 * 而不是一个和被测行为无关的崩溃。
 */
class FakeRepositoryIndex(
    override val accelerationSources: List<RepositoryAccelerationSource> = listOf(
        RepositoryAccelerationSource(id = "jsdelivr", name = "jsDelivr", description = "默认"),
        RepositoryAccelerationSource(id = "moeyy", name = "Moeyy", description = "备用")
    )
) : RepositoryIndex {

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

