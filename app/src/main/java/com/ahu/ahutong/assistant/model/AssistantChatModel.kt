package com.ahu.ahutong.assistant.model

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** 与 OpenAI 兼容接口对齐的最小消息/工具类型，避免把厂商协议扩散到能力层。 */
data class AssistantToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
)

/** 随 user 消息发送的图片；url 为 data URL 或 https 地址。 */
data class AssistantImage(
    val url: String,
    val label: String = "",
)

data class AssistantMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<AssistantToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
    /** 仅 user 消息使用；非空时 content 会序列化为 text + image_url 数组。 */
    val images: List<AssistantImage> = emptyList(),
)

data class AssistantToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class AssistantTokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val cachedTokens: Int? = null,
) {
    operator fun plus(other: AssistantTokenUsage): AssistantTokenUsage = AssistantTokenUsage(
        promptTokens = promptTokens + other.promptTokens,
        completionTokens = completionTokens + other.completionTokens,
        totalTokens = totalTokens + other.totalTokens,
        cachedTokens = listOfNotNull(cachedTokens, other.cachedTokens).takeIf { it.isNotEmpty() }?.sum(),
    )
}

data class AssistantModelReply(
    val content: String? = null,
    val toolCalls: List<AssistantToolCall> = emptyList(),
    /** 厂商返回的本轮 usage；部分兼容接口不返回时为 null。 */
    val usage: AssistantTokenUsage? = null,
    /** 思考模型的思维链；不进入后续历史，只用于当轮展示。 */
    val reasoning: String? = null,
)

sealed interface AssistantModelEvent {
    data class Delta(val text: String) : AssistantModelEvent
    data class ReasoningDelta(val text: String) : AssistantModelEvent
    data class Completed(val reply: AssistantModelReply) : AssistantModelEvent
}

interface AssistantChatModel {
    val name: String

    /** 是否能把图片作为 user 消息内容发送；由模型库的能力标记决定。 */
    val supportsVision: Boolean get() = true

    suspend fun complete(
        messages: List<AssistantMessage>,
        tools: List<AssistantToolSpec>,
    ): AssistantModelReply

    /** 默认退化为整段返回；支持 SSE 的模型覆盖此方法逐段输出。 */
    fun stream(
        messages: List<AssistantMessage>,
        tools: List<AssistantToolSpec>,
    ): Flow<AssistantModelEvent> = flow {
        emit(AssistantModelEvent.Completed(complete(messages, tools)))
    }
}
