package com.ahu.ahutong.data.commits

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.ahu.ahutong.data.network.AhuHttp
import okhttp3.Request

/**
 * 贡献名单页的提交统计：公开静态 JSON，只读、匿名，无鉴权。
 * 单次进程内缓存；刷新入口由 ViewModel 提供。
 */
@Singleton
class AhuCommitStatsSource @Inject constructor() : CommitStatsSource {

    private val client = AhuHttp.plain(
        connectTimeoutSeconds = 10,
        readTimeoutSeconds = 15
    ).build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): List<ActiveAuthor> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(COMMITS_URL).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body?.string().orEmpty()
            json.decodeFromString<CommitsPayload>(body).authors.map { author ->
                ActiveAuthor(
                    login = author.login,
                    totalCommits = author.totalCommits,
                    commits = author.commits.map { commit ->
                        CommitRecord(
                            shortSha = commit.shortSha,
                            title = commit.title,
                            dateCst = commit.dateCst,
                            url = commit.url
                        )
                    }
                )
            }
        }
    }

    @Serializable
    private data class CommitsPayload(
        @SerialName("authors") val authors: List<AuthorPayload> = emptyList()
    )

    @Serializable
    private data class AuthorPayload(
        @SerialName("login") val login: String,
        @SerialName("totalCommits") val totalCommits: Int = 0,
        @SerialName("commits") val commits: List<CommitPayload> = emptyList()
    )

    @Serializable
    private data class CommitPayload(
        @SerialName("shortSha") val shortSha: String,
        @SerialName("title") val title: String,
        @SerialName("dateCst") val dateCst: String,
        @SerialName("url") val url: String
    )

    private companion object {
        const val COMMITS_URL = "https://inchangejiang.cn/open/ahutong-commits.json"
    }
}
