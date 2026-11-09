package com.ahu.ahutong.assistant.model

import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.google.gson.JsonObject
import java.util.Locale

/** 统一思考强度：关闭 / 低 / 中 / 高。各厂商请求字段由 [thinkingPayload] 映射。 */
enum class AssistantThinkingLevel(val id: String, val label: String) {
    OFF("off", "关闭"),
    LOW("low", "低"),
    MID("mid", "中"),
    HIGH("high", "高");

    companion object {
        fun fromId(value: String?): AssistantThinkingLevel =
            entries.firstOrNull { it.id == value } ?: LOW
    }
}

object AssistantThinkingPreferences {

    private const val PREFS = "assistant_preferences"
    private const val KEY_THINKING = "thinking_level"

    private val prefs by lazy {
        AppEnvironmentHolder.context().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    }

    fun level(): AssistantThinkingLevel = runCatching {
        AssistantThinkingLevel.fromId(prefs.getString(KEY_THINKING, null))
    }.getOrDefault(AssistantThinkingLevel.LOW)

    fun setLevel(level: AssistantThinkingLevel) {
        runCatching { prefs.edit().putString(KEY_THINKING, level.id).apply() }
    }
}

internal enum class ThinkingProvider {
    DEEPSEEK,
    KIMI,
    QWEN,
    GLM,
    OPENAI,
    MINIMAX,
    ARK,
    UNKNOWN,
}

internal fun detectThinkingProvider(baseUrl: String, model: String): ThinkingProvider {
    val url = baseUrl.lowercase(Locale.ROOT)
    val name = model.lowercase(Locale.ROOT)
    // 先看模型名，兼容把多个厂商挂在同一个代理域名下的配置；再看 baseUrl。
    return when {
        name.startsWith("deepseek") -> ThinkingProvider.DEEPSEEK
        name.startsWith("kimi") -> ThinkingProvider.KIMI
        name.startsWith("qwen") -> ThinkingProvider.QWEN
        name.startsWith("glm") -> ThinkingProvider.GLM
        name.startsWith("minimax") -> ThinkingProvider.MINIMAX
        name.startsWith("doubao") -> ThinkingProvider.ARK
        name.startsWith("gpt") || name.startsWith("chatgpt") ||
            Regex("^o[1-9]").containsMatchIn(name) -> ThinkingProvider.OPENAI
        "deepseek" in url -> ThinkingProvider.DEEPSEEK
        "moonshot" in url || "kimi" in url -> ThinkingProvider.KIMI
        "dashscope" in url || "aliyun" in url -> ThinkingProvider.QWEN
        "bigmodel" in url || "zhipu" in url -> ThinkingProvider.GLM
        "minimax" in url -> ThinkingProvider.MINIMAX
        "volces" in url || "ark" in url -> ThinkingProvider.ARK
        "openai.com" in url -> ThinkingProvider.OPENAI
        else -> ThinkingProvider.UNKNOWN
    }
}

/**
 * 生成请求体里与思考相关的字段；空对象表示不发送任何思考参数。
 * 档位映射遵循各厂商当前文档；DeepSeek 没有 medium，mid 落到 high。
 */
internal fun thinkingPayload(
    provider: ThinkingProvider,
    level: AssistantThinkingLevel,
    model: String,
): JsonObject {
    val body = JsonObject()
    when (provider) {
        ThinkingProvider.DEEPSEEK -> {
            if (level == AssistantThinkingLevel.OFF) {
                body.addThinkingType("disabled")
            } else {
                body.addThinkingType("enabled")
                body.addProperty(
                    "reasoning_effort",
                    when (level) {
                        AssistantThinkingLevel.LOW -> "low"
                        AssistantThinkingLevel.MID -> "high"
                        AssistantThinkingLevel.HIGH -> "max"
                        AssistantThinkingLevel.OFF -> "none"
                    }
                )
            }
        }

        ThinkingProvider.OPENAI -> {
            body.addProperty(
                "reasoning_effort",
                when (level) {
                    AssistantThinkingLevel.OFF -> "none"
                    AssistantThinkingLevel.LOW -> "low"
                    AssistantThinkingLevel.MID -> "medium"
                    AssistantThinkingLevel.HIGH -> "high"
                }
            )
        }

        // K3 系列始终思考，只用顶层 reasoning_effort；更早的 K2.x 用 thinking 开关。
        ThinkingProvider.KIMI -> {
            if (model.lowercase(Locale.ROOT).startsWith("kimi-k3")) {
                body.addProperty(
                    "reasoning_effort",
                    when (level) {
                        AssistantThinkingLevel.OFF, AssistantThinkingLevel.LOW -> "low"
                        AssistantThinkingLevel.MID -> "high"
                        AssistantThinkingLevel.HIGH -> "max"
                    }
                )
            } else {
                body.addThinkingType(if (level == AssistantThinkingLevel.OFF) "disabled" else "enabled")
            }
        }

        ThinkingProvider.QWEN -> {
            body.addProperty("enable_thinking", level != AssistantThinkingLevel.OFF)
            if (level != AssistantThinkingLevel.OFF) {
                body.addProperty(
                    "reasoning_effort",
                    when (level) {
                        AssistantThinkingLevel.LOW -> "low"
                        AssistantThinkingLevel.MID -> "medium"
                        AssistantThinkingLevel.HIGH -> "xhigh"
                        AssistantThinkingLevel.OFF -> "low"
                    }
                )
            }
        }

        ThinkingProvider.GLM -> {
            body.addThinkingType(if (level == AssistantThinkingLevel.OFF) "disabled" else "enabled")
            if (level != AssistantThinkingLevel.OFF) {
                body.addProperty(
                    "reasoning_effort",
                    when (level) {
                        AssistantThinkingLevel.LOW -> "low"
                        AssistantThinkingLevel.MID -> "high"
                        AssistantThinkingLevel.HIGH -> "max"
                        AssistantThinkingLevel.OFF -> "low"
                    }
                )
            }
        }

        ThinkingProvider.MINIMAX -> {
            body.addThinkingType(if (level == AssistantThinkingLevel.OFF) "disabled" else "adaptive")
            if (level != AssistantThinkingLevel.OFF) {
                body.addProperty(
                    "reasoning_effort",
                    when (level) {
                        AssistantThinkingLevel.LOW -> "low"
                        AssistantThinkingLevel.MID -> "high"
                        AssistantThinkingLevel.HIGH -> "max"
                        AssistantThinkingLevel.OFF -> "low"
                    }
                )
            }
        }

        ThinkingProvider.ARK -> {
            body.addThinkingType(if (level == AssistantThinkingLevel.OFF) "disabled" else "enabled")
            if (level != AssistantThinkingLevel.OFF) {
                body.addProperty(
                    "reasoning_effort",
                    when (level) {
                        AssistantThinkingLevel.LOW -> "low"
                        AssistantThinkingLevel.MID -> "medium"
                        AssistantThinkingLevel.HIGH -> "high"
                        AssistantThinkingLevel.OFF -> "low"
                    }
                )
            }
        }

        ThinkingProvider.UNKNOWN -> Unit
    }
    return body
}

private fun JsonObject.addThinkingType(type: String) {
    add("thinking", JsonObject().apply { addProperty("type", type) })
}

/** 仅供新增模型时给出默认能力标记，用户可在编辑页改。 */
internal fun guessSupportsThinking(baseUrl: String, model: String): Boolean {
    val name = model.lowercase(Locale.ROOT)
    if (':' in name) return false // Ollama 这类本地标签，参数规范不确定
    return detectThinkingProvider(baseUrl, model) != ThinkingProvider.UNKNOWN
}

internal fun guessSupportsVision(baseUrl: String, model: String): Boolean {
    val name = model.lowercase(Locale.ROOT)
    if (name.contains("deepseek-v4-pro")) return false
    if (':' in name) return name.contains("vl") || name.contains("vision")
    return listOf(
        "vl", "vision", "gemma3", "gemma4", "gpt-4o", "gpt-4.1", "gpt-5",
        "claude", "gemini", "kimi-k2.5", "kimi-k3", "glm-4v", "glm-5v", "doubao",
    ).any { it in name } || detectThinkingProvider(baseUrl, model) == ThinkingProvider.DEEPSEEK
}

internal fun providerLabelFor(baseUrl: String, model: String): String = when (
    detectThinkingProvider(baseUrl, model)
) {
    ThinkingProvider.DEEPSEEK -> "DeepSeek"
    ThinkingProvider.KIMI -> "Kimi"
    ThinkingProvider.QWEN -> "通义千问"
    ThinkingProvider.GLM -> "智谱 GLM"
    ThinkingProvider.MINIMAX -> "MiniMax"
    ThinkingProvider.ARK -> "豆包"
    ThinkingProvider.OPENAI -> "OpenAI"
    ThinkingProvider.UNKNOWN -> "自定义"
}
