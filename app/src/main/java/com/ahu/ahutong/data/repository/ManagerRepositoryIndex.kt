package com.ahu.ahutong.data.repository

import com.ahu.ahutong.core.common.AppEnvironmentHolder
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RepositoryIndex] 的生产实现：委托给 :app 的 [RepositoryManager]。
 *
 * 为什么保留这层委托、而不是让管理器直接实现接口：管理器的方法都带默认参数，
 * 而实现接口的覆盖函数不允许再声明默认值——直接实现会让 30 多处既有调用点全部编译失败。
 * 委托层让"接口给出默认值、实现照旧被调用"各就各位；P4 把管理器搬进本模块时再合并。
 *
 * 需要 Context 的几个方法在这里自己向应用环境要——调用方因此不必持有 Context，
 * 这也是 feature 的 ViewModel 能在 JVM 上被测试的前提。
 */
@Singleton
class ManagerRepositoryIndex @Inject constructor() : RepositoryIndex {

    override val accelerationSources: List<RepositoryAccelerationSource>
        get() = RepositoryManager.accelerationSources

    override suspend fun getContents(path: String, forceRefresh: Boolean): List<GitHubContentItem> =
        RepositoryManager.getContents(path, forceRefresh)

    override fun getCachedContents(path: String): CachedRepositoryContents? =
        RepositoryManager.getCachedContents(path)

    override suspend fun warmUpAllContentCaches(
        forceRefresh: Boolean,
        onProgress: ((Int) -> Unit)?,
        onDownloadProgress: ((Long, Long) -> Unit)?
    ): Long = RepositoryManager.warmUpAllContentCaches(forceRefresh, onProgress, onDownloadProgress)

    override fun getDirectorySummaries(
        items: List<GitHubContentItem>
    ): Map<String, RepositoryDirectorySummary> = RepositoryManager.getDirectorySummaries(items)

    override fun shouldShowUnsupportedDirectoryMessage(path: String): Boolean =
        RepositoryManager.shouldShowUnsupportedDirectoryMessage(path)

    override suspend fun getMarkdownDocument(path: String): RepositoryMarkdownDocument =
        RepositoryManager.getMarkdownDocument(path)

    override fun getRawUrl(path: String): String = RepositoryManager.getRawUrl(path)

    override fun getGitHubUrl(path: String): String = RepositoryManager.getGitHubUrl(path)

    override fun getRepositoryTitle(repoId: String): String =
        RepositoryManager.getRepositoryTitle(repoId)

    override fun getRepositoryOrder(path: String): Int = RepositoryManager.getRepositoryOrder(path)

    override fun formatDisplayPath(path: String): String =
        RepositoryManager.formatDisplayPath(path)

    override suspend fun downloadFile(
        path: String,
        onProgress: (Float) -> Unit
    ): DownloadedFile? =
        RepositoryManager.downloadFile(path, AppEnvironmentHolder.context(), onProgress)

    override fun getDownloadedFiles(): List<DownloadedFile> =
        RepositoryManager.getDownloadedFiles(AppEnvironmentHolder.context())

    override fun getDownloadedFile(path: String): DownloadedFile? =
        RepositoryManager.getDownloadedFile(path, AppEnvironmentHolder.context())

    override fun getLocalFile(path: String): File? =
        RepositoryManager.getLocalFile(path, AppEnvironmentHolder.context())

    override fun deleteFile(path: String): Boolean =
        RepositoryManager.deleteFile(path, AppEnvironmentHolder.context())
}
