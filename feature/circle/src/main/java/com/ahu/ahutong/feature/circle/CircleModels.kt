package com.ahu.ahutong.feature.circle

/** 帖子。字段名按 BBS 接口 JSON 防御式解析（多别名兜底）。 */
data class CircleTopic(
    val id: String,
    val title: String,
    val content: String,
    val nodeName: String,
    val authorName: String,
    val commentCount: Int,
    val likeCount: Int,
    val viewCount: Int,
    val isAnon: Boolean,
    val isTop: Boolean,
    val createTime: String,
    val imageUrls: List<String>
)

/** 评论。 */
data class CircleComment(
    val id: String,
    val authorName: String,
    val content: String,
    val createTime: String
)

/**
 * 索引服务一页：帖子 id 序列 + 签名游标（契约照抄 AHU Plus 的 /market/readonly/feed）。
 * 列表接口已被上游强制鉴权，客户端改走「索引拿 id → 只读口按 id 取正文」的两段式，
 * 全程零凭证；翻页语义为 (createTime DESC, topicId DESC) 复合游标。
 */
data class CircleIndexPage(
    val ids: List<String>,
    val nextCursor: String?,
    val hasMore: Boolean,
    /** 上游同步健康度：ready 之外的值表示索引侧同步中，内容可能不完整。 */
    val sourceStatus: String
) {
    val isReady: Boolean get() = sourceStatus.isBlank() || sourceStatus == "ready"
}