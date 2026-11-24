package com.ahu.ahutong.data.wall

import com.google.gson.annotations.SerializedName

/** 校园墙作者；上游可能不返回，字段可空。 */
data class WallAuthor(
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
)

/** 时间线帖子。字段与 https://inchangejiang.cn/wall/feed 一一对应。 */
data class WallPost(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("node") val node: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("images") val images: List<String>? = null,
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("comments") val comments: Int = 0,
    @SerializedName("views") val views: Int = 0,
    @SerializedName("create_time") val createTime: String? = null,
    @SerializedName("created_at_ts") val createdAtTs: Long = 0,
    @SerializedName("author") val author: WallAuthor? = null,
    @SerializedName("masked") val masked: Boolean = false,
)

data class WallFeedResponse(
    @SerializedName("items") val items: List<WallPost>? = null,
)

data class WallComment(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("user") val user: WallAuthor? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("parent_comment_id") val parentCommentId: Long = 0,
    @SerializedName("reply_to_comment_id") val replyToCommentId: Long = 0,
    @SerializedName("create_time") val createTime: String? = null,
)

data class WallTopic(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("node") val node: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("images") val images: List<String>? = null,
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("comments") val comments: Int = 0,
    @SerializedName("views") val views: Int = 0,
    @SerializedName("create_time") val createTime: String? = null,
    @SerializedName("created_at_ts") val createdAtTs: Long = 0,
    @SerializedName("author") val author: WallAuthor? = null,
    @SerializedName("masked") val masked: Boolean = false,
    @SerializedName("comment_list") val commentList: List<WallComment>? = null,
)

data class WallSearchItem(
    @SerializedName("topic_id") val topicId: Long = 0,
    @SerializedName("kind") val kind: String? = null,
    @SerializedName("node") val node: String? = null,
    @SerializedName("snippet") val snippet: String? = null,
    @SerializedName("create_time") val createTime: String? = null,
    @SerializedName("ts") val ts: Long = 0,
)

data class WallHotResponse(
    @SerializedName("items") val items: List<WallPost>? = null,
    @SerializedName("cached_at") val cachedAt: Long = 0,
    @SerializedName("stale") val stale: Boolean = false,
)

data class WallSearchResponse(
    @SerializedName("query") val query: String? = null,
    @SerializedName("items") val items: List<WallSearchItem>? = null,
)

data class WallStatus(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("now") val now: Long = 0,
    @SerializedName("last_sync_at") val lastSyncAt: Long = 0,
    @SerializedName("token_exp") val tokenExp: Long? = null,
    @SerializedName("window_days") val windowDays: Int = 3,
    @SerializedName("topics_in_window") val topicsInWindow: Int = 0,
    @SerializedName("comments_in_window") val commentsInWindow: Int = 0,
    @SerializedName("comment_queue") val commentQueue: Int = 0,
)
