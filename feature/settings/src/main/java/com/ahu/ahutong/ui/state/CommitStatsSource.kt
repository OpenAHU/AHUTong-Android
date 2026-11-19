package com.ahu.ahutong.ui.state

/** 开源提交统计：接口在这里，HTTP 实现在 :app。 */
interface CommitStatsSource {
    suspend fun load(): List<ActiveAuthor>
}

data class ActiveAuthor(
    val login: String,
    val totalCommits: Int,
    val commits: List<CommitRecord>
)

data class CommitRecord(
    val shortSha: String,
    val title: String,
    val dateCst: String,
    val url: String
)
