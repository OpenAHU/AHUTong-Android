package com.ahu.ahutong.data.repository

import com.google.gson.annotations.SerializedName

/**
 * 资料仓库内容项（目录或文件）。
 *
 * [id] 由 ahutong-storage 服务端索引分配（sha256(repoId:path)），/api/download 用它
 * 定位文件；仅文件条目有值，目录为 null。字段追加在末尾，避免影响既有的位置参数调用。
 */
data class GitHubContentItem(
    val name: String,
    val path: String,
    val type: String,        // "file" or "dir"
    val size: Long = 0,
    @SerializedName("download_url")
    val downloadUrl: String? = null,
    @SerializedName("html_url")
    val htmlUrl: String? = null,
    val repositoryId: String? = null,
    val repositoryPath: String? = null,
    val containsOnlyUnsupportedFiles: Boolean = false,
    val id: String? = null
)

/**
 * 本地下载记录
 */
data class DownloadedFile(
    val name: String,
    val path: String,         // 仓库虚拟路径
    val localPath: String,    // 本地文件路径
    val size: Long = 0,
    val downloadTime: Long = System.currentTimeMillis(),
    val uri: String? = null
)

/**
 * 本地缓存的目录内容
 */
data class CachedRepositoryContents(
    val items: List<GitHubContentItem>,
    val updateTime: Long
)

data class RepositoryDirectorySummary(
    val directoryCount: Int = 0,
    val fileCount: Int = 0
)

data class RepositoryMarkdownDocument(
    val title: String,
    val path: String,
    val content: String
)

data class RepositoryAccelerationSource(
    val id: String,
    val name: String,
    val description: String,
    val proxyPrefix: String? = null,
    val useJsDelivr: Boolean = false,
    val isFallback: Boolean = false
)

/**
 * ahutong-storage /api/update 应答：索引更新时间戳（毫秒）。
 */
data class StorageUpdateResponse(
    val updatedAt: Long = 0
)

/**
 * ahutong-storage /api/list 应答：完整资料索引。
 */
data class StorageIndexResponse(
    val updatedAt: Long = 0,
    val repositories: List<StorageRepository> = emptyList(),
    val files: List<StorageFileEntry> = emptyList(),
    val dirs: List<StorageDirEntry> = emptyList()
)

data class StorageRepository(
    val id: String,
    val title: String,
    val owner: String,
    val repo: String,
    val branch: String,
    val commit: String = "",
    val files: Int = 0
)

data class StorageFileEntry(
    val id: String,
    val repo: String,
    val path: String,
    val name: String,
    val size: Long = 0,
    val lfs: Boolean = false
)

data class StorageDirEntry(
    val repo: String,
    val path: String,
    val state: String = "normal"
)

/**
 * ahutong-storage /api/download 对非 LFS 文件的 link 应答：给出 GitHub 原始链接，
 * 是否拼接加速前缀由客户端按当前下载源决定。
 */
data class StorageLinkResponse(
    val url: String,
    val name: String? = null,
    val size: Long = 0
)
