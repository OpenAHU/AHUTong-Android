package com.ahu.ahutong.assistant.model

/**
 * 模型配置：OpenAI 兼容接口（Base URL + 模型名 + API Key）。
 * Key 由 [AssistantModelLibrary] 按条存进 Android Keystore，不落明文、不进日志。
 */
data class AssistantModelConfig(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    /** 能力标记由模型库维护，请求层据此决定是否发送思考/图片参数。 */
    val supportsVision: Boolean = false,
    val supportsThinking: Boolean = true,
    val providerLabel: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    /** API Key 只能走 HTTPS；仅为本机开发地址保留明文 HTTP。 */
    val isSecureEndpoint: Boolean
        get() {
            val value = baseUrl.trim().lowercase()
            if (value.startsWith("https://")) return true
            if (!value.startsWith("http://")) return false
            val host = value.removePrefix("http://")
                .substringBefore('/')
                .substringBefore(':')
            return host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "10.0.2.2"
        }

    fun endpoint(): String = baseUrl.trimEnd('/') + "/chat/completions"

    override fun toString(): String = "AssistantModelConfig(baseUrl=$baseUrl, model=$model, apiKey=***)"
}

object AssistantModelConfigStore {

    fun load(): AssistantModelConfig? = AssistantModelLibrary.activeConfig()

    fun save(config: AssistantModelConfig) {
        AssistantModelLibrary.saveActive(config)
    }

    fun clear() {
        AssistantModelLibrary.clear()
    }
}
