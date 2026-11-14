package com.ahu.ahutong.assistant.model

import com.ahu.ahutong.data.network.AhuHttp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI 兼容的对话模型（支持工具调用 + SSE 流式）。
 * 只负责"发请求 / 解析返回"，不碰工具执行与审批——那部分在 AssistantRuntime 与安全层。
 */
class OpenAiCompatibleChatModel(
    private val config: AssistantModelConfig,
    private val thinkingLevel: AssistantThinkingLevel = AssistantThinkingPreferences.level(),
    private val maxTokens: Int? = null,
    private val client: OkHttpClient = AhuHttp.plain(
        connectTimeoutSeconds = 15,
        readTimeoutSeconds = 90,
        captureDiagnostics = false,
        followRedirects = config.baseUrl != OfficialTrial.profile.baseUrl,
    ).build(),
) : AssistantChatModel {

    override val name: String get() = config.model
    override val supportsVision: Boolean get() = config.supportsVision
    private val officialTrial: Boolean
        get() = config.baseUrl == OfficialTrial.profile.baseUrl && config.model == OfficialTrial.profile.model
    private val trialOwner = if (officialTrial) OfficialTrial.currentIdentity() else null

    override suspend fun complete(
        messages: List<AssistantMessage>,
        tools: List<AssistantToolSpec>,
    ): AssistantModelReply = withContext(Dispatchers.IO) {
        require(config.isConfigured) { "模型还没配置" }
        client.newCall(buildRequest(messages, tools, stream = false)).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (isContentRejection(body)) throw ModelContentRejectedException(body.take(300))
                throw IOException("模型接口返回 ${response.code}：${body.take(300)}")
            }
            val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrElse {
                throw IOException("模型响应无法解析：${body.take(300)}")
            }
            val message = root.getAsJsonArray("choices")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("message")
                ?: throw IOException("模型没有返回候选结果")
            AssistantModelReply(
                content = message.stringOrNull("content"),
                toolCalls = message.parseToolCalls(),
                usage = root.parseUsage(),
                reasoning = message.stringOrNull("reasoning_content")
                    ?: message.stringOrNull("reasoning"),
            )
        }
    }

    override fun stream(
        messages: List<AssistantMessage>,
        tools: List<AssistantToolSpec>,
    ): Flow<AssistantModelEvent> = flow {
        require(config.isConfigured) { "模型还没配置" }
        var response = client.newCall(buildRequest(messages, tools, stream = true)).execute()
        if (!response.isSuccessful && response.code in 400..422) {
            val code = response.code
            val body = response.body?.string().orEmpty()
            response.close()
            if (isContentRejection(body)) throw ModelContentRejectedException(body.take(300))
            // 少数 OpenAI 兼容实现不认 stream_options；只在这种情况下退回不带 usage 的请求。
            val rejectedUsageOption = body.contains("stream_options", ignoreCase = true) ||
                body.contains("include_usage", ignoreCase = true) ||
                body.contains("unknown", ignoreCase = true)
            if (!rejectedUsageOption) {
                throw IOException("模型接口返回 $code：${body.take(300)}")
            }
            response = client.newCall(buildRequest(messages, tools, stream = true, includeUsage = false))
                .execute()
        }
        response.use {
            if (!it.isSuccessful) {
                val body = it.body?.string().orEmpty()
                throw IOException("模型接口返回 ${it.code}：${body.take(300)}")
            }
            val source = it.body?.source() ?: throw IOException("模型流为空")
            val assembler = AssistantStreamAssembler()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                assembler.accept(data)?.let { delta ->
                    delta.reasoning?.let { emit(AssistantModelEvent.ReasoningDelta(it)) }
                    delta.content?.let { emit(AssistantModelEvent.Delta(it)) }
                }
            }
            emit(AssistantModelEvent.Completed(assembler.reply()))
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequest(
        messages: List<AssistantMessage>,
        tools: List<AssistantToolSpec>,
        stream: Boolean,
        includeUsage: Boolean = stream,
    ): Request {
        val officialStudent = if (officialTrial) {
            val owner = trialOwner
            if (owner == null || owner != OfficialTrial.currentIdentity()) {
                throw IOException("校园账号已切换，请重新打开对话。")
            }
            owner.userId
        } else null
        val payload = JsonObject().apply {
            addProperty("model", config.model)
            add("messages", JsonArray().apply {
                messages.forEach { message ->
                    val json = message.toJson()
                    add(officialStudent?.let { OfficialTrial.redactStudentNumber(json, it) } ?: json)
                }
            })
            if (tools.isNotEmpty()) {
                add("tools", JsonArray().apply {
                    tools.forEach { spec ->
                        add(
                            JsonObject().apply {
                                addProperty("type", "function")
                                add(
                                    "function",
                                    JsonObject().apply {
                                        addProperty("name", spec.name)
                                        addProperty("description", spec.description)
                                        add("parameters", spec.parameters)
                                    }
                                )
                            }
                        )
                    }
                })
                addProperty("tool_choice", "auto")
            }
            // 统一档位按模型所属厂商映射；无能力标记或未知厂商时不发思考参数。
            val thinking = if (config.supportsThinking) {
                thinkingPayload(
                    detectThinkingProvider(config.baseUrl, config.model),
                    thinkingLevel,
                    config.model,
                )
            } else {
                JsonObject()
            }
            thinking.entrySet().forEach { (key, value) -> add(key, value) }
            // 思考模式下不少厂商会拒绝非 1 的 temperature（DeepSeek 则忽略），只在非思考时发送。
            if (thinking.size() == 0 || thinkingLevel == AssistantThinkingLevel.OFF) {
                addProperty("temperature", 0.2)
            }
            maxTokens?.let { addProperty("max_tokens", it) }
            addProperty("stream", stream)
            if (stream && includeUsage) {
                add("stream_options", JsonObject().apply { addProperty("include_usage", true) })
            }
        }
        return Request.Builder()
            .url(config.endpoint())
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }
}

internal fun AssistantMessage.toJson(): JsonObject = JsonObject().apply {
    addProperty("role", role)
    if (role == "user" && images.isNotEmpty()) {
        add("content", JsonArray().apply {
            content?.takeIf { it.isNotBlank() }?.let { text ->
                add(
                    JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", text)
                    }
                )
            }
            images.forEach { image ->
                add(
                    JsonObject().apply {
                        addProperty("type", "image_url")
                        add(
                            "image_url",
                            JsonObject().apply { addProperty("url", image.url) }
                        )
                    }
                )
            }
        })
    } else {
        content?.let { addProperty("content", it) }
    }
    if (toolCalls.isNotEmpty()) {
        add("tool_calls", JsonArray().apply {
            toolCalls.forEach { call ->
                add(
                    JsonObject().apply {
                        addProperty("id", call.id)
                        addProperty("type", "function")
                        add(
                            "function",
                            JsonObject().apply {
                                addProperty("name", call.name)
                                addProperty("arguments", call.arguments.toString())
                            }
                        )
                    }
                )
            }
        })
    }
    toolCallId?.let { addProperty("tool_call_id", it) }
    name?.let { addProperty("name", it) }
}

private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { !it.isJsonNull }?.asString

private val CONTENT_REJECTION_HINTS = listOf(
    "content_filter", "content_policy", "content_policy_violation", "safety", "moderation",
    "审核", "违规", "敏感", "不合规", "内容安全",
)

internal fun isContentRejection(body: String): Boolean =
    CONTENT_REJECTION_HINTS.any { body.contains(it, ignoreCase = true) }

/**
 * 兼容 OpenAI / DeepSeek / Kimi / Qwen 等 OpenAI 协议的 usage：
 * 常见为顶层 usage，少数实现在 choices[0].usage；缓存字段名也不统一。
 */
internal fun JsonObject.parseUsage(): AssistantTokenUsage? {
    val usage = runCatching { getAsJsonObject("usage") }.getOrNull()
        ?: runCatching {
            getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("usage")
        }.getOrNull()
        ?: return null
    fun number(key: String): Int? =
        usage.get(key)?.takeIf { !it.isJsonNull }?.asInt
    val prompt = number("prompt_tokens") ?: number("input_tokens") ?: 0
    val completion = number("completion_tokens") ?: number("output_tokens") ?: 0
    val total = number("total_tokens") ?: (prompt + completion)
    val cached = runCatching {
        usage.getAsJsonObject("prompt_tokens_details")?.get("cached_tokens")
            ?.takeIf { !it.isJsonNull }?.asInt
    }.getOrNull() ?: number("prompt_cache_hit_tokens")
    if (prompt == 0 && completion == 0 && total == 0) return null
    return AssistantTokenUsage(
        promptTokens = prompt,
        completionTokens = completion,
        totalTokens = total,
        cachedTokens = cached,
    )
}

private fun JsonObject.parseToolCalls(): List<AssistantToolCall> {
    val array = runCatching { getAsJsonArray("tool_calls") }.getOrNull() ?: return emptyList()
    return array.mapNotNull { element ->
        val item = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
        val function = runCatching { item.getAsJsonObject("function") }.getOrNull()
            ?: return@mapNotNull null
        val name = function.stringOrNull("name").orEmpty()
        if (name.isBlank()) return@mapNotNull null
        AssistantToolCall(
            id = item.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = name,
            arguments = runCatching {
                JsonParser.parseString(function.stringOrNull("arguments").orEmpty().ifBlank { "{}" })
                    .asJsonObject
            }.getOrElse { JsonObject() },
        )
    }
}

/** 流式分片的累加器：内容与多个 tool_call 的 arguments 分片可能交错到达。 */
internal data class AssembledDelta(
    val content: String? = null,
    val reasoning: String? = null,
)

internal class AssistantStreamAssembler {

    private val content = StringBuilder()
    private val reasoning = StringBuilder()
    private val calls = sortedMapOf<Int, Accumulator>()
    private var usage: AssistantTokenUsage? = null

    /** 返回本次新增的正文/思维链（都没有则为 null）。 */
    fun accept(data: String): AssembledDelta? {
        val root = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: return null
        root.parseUsage()?.let { usage = it }
        val delta = root.getAsJsonArray("choices")
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("delta")
            ?: return null

        val newReasoning = (delta.stringOrNull("reasoning_content") ?: delta.stringOrNull("reasoning"))
            ?.takeIf { it.isNotEmpty() }
            ?.also { reasoning.append(it) }
        val newContent = delta.stringOrNull("content")
            ?.takeIf { it.isNotEmpty() }
            ?.also { content.append(it) }

        val toolCalls = runCatching { delta.getAsJsonArray("tool_calls") }.getOrNull()
        toolCalls?.forEachIndexed { position, element ->
            val item = runCatching { element.asJsonObject }.getOrNull() ?: return@forEachIndexed
            val index = item.get("index")?.takeIf { !it.isJsonNull }?.asInt ?: position
            val acc = calls.getOrPut(index) { Accumulator() }
            item.stringOrNull("id")?.takeIf { it.isNotBlank() }?.let { acc.id = it }
            val function = runCatching { item.getAsJsonObject("function") }.getOrNull()
            function?.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let { acc.name = it }
            function?.stringOrNull("arguments")?.let { acc.arguments.append(it) }
        }
        if (newReasoning == null && newContent == null) return null
        return AssembledDelta(content = newContent, reasoning = newReasoning)
    }

    fun reply(): AssistantModelReply = AssistantModelReply(
        content = content.toString().ifBlank { null },
        reasoning = reasoning.toString().ifBlank { null },
        toolCalls = calls.values.mapNotNull { acc ->
            if (acc.name.isBlank()) return@mapNotNull null
            AssistantToolCall(
                id = acc.id.ifBlank { UUID.randomUUID().toString() },
                name = acc.name,
                arguments = runCatching {
                    JsonParser.parseString(acc.arguments.toString().ifBlank { "{}" }).asJsonObject
                }.getOrElse { JsonObject() },
            )
        },
        usage = usage,
    )

    private class Accumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }
}
