package com.ahu.ahutong.data.repository

import java.io.File

/**
 * 仓库索引与文档下载的对外接口（计划 §3.2 的 RepositoryIndex）。
 *
 * 界面只依赖它：能读目录、取文档、下载与删除本地文件，但不必认识 GitHub/JsDelivr 协议、
 * 缓存分层或下载实现。生产实现是 :app 的 RepositoryManager，测试可以用记录型 fake。
 *
 * 接口里**不出现 Context**：下载与导出确实需要它，但那是实现的细节——
 * 适配器自己向应用环境要，调用方（feature 的 ViewModel）因此可以在 JVM 上被测试。
 */
interface RepositoryIndex {

    /** 可选的下载加速源，供设置界面展示。 */
    val accelerationSources: List<RepositoryAccelerationSource>

    suspend fun getContents(path: String = "", forceRefresh: Boolean = false): List<GitHubContentItem>

    fun getCachedContents(path: String = ""): CachedRepositoryContents?

    suspend fun warmUpAllContentCaches(
        forceRefresh: Boolean = false,
        onProgress: ((Int) -> Unit)? = null,
        onDownloadProgress: ((Long, Long) -> Unit)? = null
    ): Long

    fun getDirectorySummaries(items: List<GitHubContentItem>): Map<String, RepositoryDirectorySummary>

    fun shouldShowUnsupportedDirectoryMessage(path: String): Boolean

    suspend fun getMarkdownDocument(path: String): RepositoryMarkdownDocument

    fun getRawUrl(path: String): String

    fun getGitHubUrl(path: String): String

    fun getRepositoryTitle(repoId: String): String

    fun getRepositoryOrder(path: String): Int

    /** 把虚拟路径渲染成带仓库标题的展示路径（纯格式化，无 IO）。 */
    fun formatDisplayPath(path: String): String

    suspend fun downloadFile(
        path: String,
        onProgress: (Float) -> Unit = {}
    ): DownloadedFile?

    fun getDownloadedFiles(): List<DownloadedFile>

    fun getDownloadedFile(path: String): DownloadedFile?

    fun getLocalFile(path: String): File?

    fun deleteFile(path: String): Boolean
}
