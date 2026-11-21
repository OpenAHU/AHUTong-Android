package com.ahu.ahutong.data.commits

/** 开源提交统计：接口与模型放在共享模型层，HTTP 实现留在 :app。 */
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
