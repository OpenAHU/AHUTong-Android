package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantCard
import com.ahu.ahutong.assistant.AssistantCardItem
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.assistant.AssistantTrust
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.wall.CampusWallRepository
import com.ahu.ahutong.data.wall.WallSearchItem
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 校园墙检索与取帖：只读最近 3 天的墙内容。
 * 墙内容是同学发帖，未经核实，统一标记 UNTRUSTED，模型只能当作参考数据。
 */
class SearchCampusWallCapability(
    private val repository: CampusWallRepository = CampusWallRepository(),
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "search_campus_wall",
        title = "搜索校园墙",
        description = "检索最近 3 天校园墙的帖子与评论片段。内容来自同学发帖，未经证实，仅供参考。",
        parameters = JsonParser.parseString(
            """{"type":"object","properties":{"query":{"type":"string","description":"检索关键词"},"limit":{"type":"integer","minimum":1,"maximum":20}},"required":["query"],"additionalProperties":false}"""
        ).asJsonObject,
        sensitivity = AssistantSensitivity.PUBLIC,
        source = "校园墙",
        openRoute = "campus_circle",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val query = arguments.get("query")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        if (query.isBlank()) {
            return AssistantOutcome.Failure("empty_query", "请提供要检索的关键词")
        }
        val limit = arguments.get("limit")?.takeIf { it.isJsonPrimitive }?.asInt?.coerceIn(1, 20) ?: 10
        return when (val result = repository.search(query, limit)) {
            is AhuResult.Success -> {
                val items = result.value.items.orEmpty()
                AssistantOutcome.Success(
                    AssistantAnswer(
                        summary = if (items.isEmpty()) {
                            "最近三天的校园墙没有相关信息"
                        } else {
                            "校园墙找到 ${items.size} 条相关内容（最近 3 天，未经证实）"
                        },
                        lines = items.map { it.toLine() },
                        trust = AssistantTrust.UNTRUSTED,
                        card = if (items.isEmpty()) null else AssistantCard(
                            kind = "wall_search",
                            headline = "校园墙检索",
                            subtitle = "最近 3 天，内容未经证实",
                            items = items.map { item ->
                                AssistantCardItem(
                                    title = item.snippet.orEmpty().cleanWallSnippet().ifBlank { "（无正文片段）" },
                                    subtitle = listOfNotNull(
                                        item.node?.takeIf { it.isNotBlank() },
                                        formatTime(item.createTime)
                                    ).joinToString("  "),
                                    value = "#${item.topicId}",
                                )
                            },
                        ),
                    )
                )
            }
            is AhuResult.Failure -> result.error.asAssistantFailure()
        }
    }
}

class ReadCampusTopicCapability(
    private val repository: CampusWallRepository = CampusWallRepository(),
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "read_campus_topic",
        title = "读取校园墙帖子",
        description = "按帖子 ID 读取校园墙正文与已同步评论。内容来自同学发帖，未经证实，仅供参考。",
        parameters = JsonParser.parseString(
            """{"type":"object","properties":{"topic_id":{"type":"integer","description":"search_campus_wall 返回的 topic_id"}},"required":["topic_id"],"additionalProperties":false}"""
        ).asJsonObject,
        sensitivity = AssistantSensitivity.PUBLIC,
        source = "校园墙",
        openRoute = "campus_circle",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val id = arguments.get("topic_id")?.takeIf { it.isJsonPrimitive }?.asLong
        if (id == null || id <= 0) {
            return AssistantOutcome.Failure("invalid_topic", "缺少有效的帖子 ID")
        }
        return when (val result = repository.topic(id)) {
            is AhuResult.Success -> {
                val topic = result.value
                val comments = topic.commentList.orEmpty()
                val byId = comments.associateBy { it.id }
                AssistantOutcome.Success(
                    AssistantAnswer(
                        summary = buildString {
                            append("校园墙帖子 #${topic.id}")
                            topic.node?.takeIf { it.isNotBlank() }?.let { append("（$it）") }
                        },
                        lines = buildList {
                            topic.title?.takeIf { it.isNotBlank() && it != "none" }?.let { add("标题：$it") }
                            add(topic.content.orEmpty().ifBlank { "（正文为空）" })
                            topic.images?.takeIf { it.isNotEmpty() }?.let { add("图片：${it.size} 张") }
                            if (comments.isNotEmpty()) {
                                add("评论（已同步 ${comments.size} 条，含楼中楼）：")
                                val topLevel = comments.filter { it.parentCommentId == 0L }
                                val replies = comments
                                    .filter { it.parentCommentId != 0L }
                                    .groupBy { it.parentCommentId }
                                topLevel.take(15).forEach { comment ->
                                    val topImages = comment.images?.takeIf { it.isNotEmpty() }
                                    add(
                                        "${comment.user?.name.orEmpty().ifBlank { "未知" }}：${comment.content.orEmpty()}" +
                                            if (topImages != null) "（图 ${topImages.size} 张）" else ""
                                    )
                                    replies[comment.id].orEmpty().take(5).forEach { reply ->
                                        val target = reply.replyToCommentId
                                            .takeIf { it != 0L }
                                            ?.let { byId[it]?.user?.name?.takeIf(String::isNotBlank) }
                                        val prefix = if (target != null) "回复 $target：" else ""
                                        val replyImages = reply.images?.takeIf { it.isNotEmpty() }
                                        add(
                                            "  └ ${reply.user?.name.orEmpty().ifBlank { "未知" }}：$prefix${reply.content.orEmpty()}" +
                                                if (replyImages != null) "（图 ${replyImages.size} 张）" else ""
                                        )
                                    }
                                }
                            } else {
                                add("评论暂未同步")
                            }
                        },
                        trust = AssistantTrust.UNTRUSTED,
                        card = AssistantCard(
                            kind = "wall_topic",
                            headline = topic.title?.takeIf { it.isNotBlank() && it != "none" } ?: "校园墙帖子",
                            subtitle = listOfNotNull(
                                topic.node?.takeIf { it.isNotBlank() },
                                formatTime(topic.createTime)
                            ).joinToString("  "),
                            note = "内容来自校园墙，未经证实",
                            items = comments.take(10).map { comment ->
                                AssistantCardItem(
                                    title = comment.user?.name.orEmpty().ifBlank { "未知" },
                                    subtitle = comment.content.orEmpty(),
                                )
                            },
                        ),
                    )
                )
            }
            is AhuResult.Failure -> result.error.asAssistantFailure()
        }
    }
}

private fun WallSearchItem.toLine(): String {
    val kind = if (kind == "comment") "评论" else "帖子"
    val meta = listOfNotNull(
        node?.takeIf { it.isNotBlank() },
        formatTime(createTime),
    ).joinToString(" ")
    return "[$kind] $meta #$topicId：${snippet.orEmpty().cleanWallSnippet()}"
}

private fun formatTime(raw: String?): String? =
    raw?.takeIf { it.length >= 16 }?.substring(5, 16)

private fun String.cleanWallSnippet(): String =
    removePrefix("none").trim('\n', '\r', ' ').replace('\n', ' ').trim()
