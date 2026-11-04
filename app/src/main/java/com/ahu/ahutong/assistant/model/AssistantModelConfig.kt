package com.ahu.ahutong.assistant.model

import com.ahu.ahutong.data.security.SecureBoxStore
import com.google.gson.Gson

/**
 * 模型配置：OpenAI 兼容接口（Base URL + 模型名 + API Key）。
 * Key 与现有支付码缓存共用 [SecureBoxStore]（Android Keystore），不落明文、不进日志。
 */
data class AssistantModelConfig(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    fun endpoint(): String = baseUrl.trimEnd('/') + "/chat/completions"

    override fun toString(): String = "AssistantModelConfig(baseUrl=$baseUrl, model=$model, apiKey=***)"
}

object AssistantModelConfigStore {

    private const val BOX = "assistant"
    private const val KEY = "model_config_v1"
    private val gson = Gson()

    fun load(): AssistantModelConfig? =
        SecureBoxStore.get(BOX, KEY)
            ?.let { raw -> runCatching { gson.fromJson(raw, AssistantModelConfig::class.java) }.getOrNull() }
            ?.takeIf { it.isConfigured }

    fun save(config: AssistantModelConfig) {
        SecureBoxStore.put(BOX, KEY, gson.toJson(config))
    }

    fun clear() {
        SecureBoxStore.remove(BOX, KEY)
    }
}
