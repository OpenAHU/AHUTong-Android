package com.ahu.ahutong.assistant

import com.google.gson.JsonObject

/**
 * AI 能力层的契约。
 *
 * 只做两件事：描述自己（给模型/UI 看），以及在既有数据层之上执行一次只读查询。
 * 不包含任何业务规则、不改动现有页面逻辑；删掉这一层，App 行为保持不变。
 */
enum class AssistantSensitivity { PUBLIC, ACADEMIC, FINANCIAL }

enum class AssistantCapabilityKind { READ, NAVIGATE }

data class AssistantCapabilitySpec(
    val id: String,
    val title: String,
    val description: String,
    /** JSON Schema；将来直接作为 LLM tool 的 parameters。 */
    val parameters: JsonObject,
    val sensitivity: AssistantSensitivity,
    val kind: AssistantCapabilityKind = AssistantCapabilityKind.READ,
    /** 结果对应的既有页面；UI 只负责跳转，不复制执行逻辑。 */
    val openRoute: String? = null,
)

data class AssistantAnswer(
    val summary: String,
    val lines: List<String> = emptyList(),
    val route: String? = null,
)

sealed interface AssistantOutcome {
    data class Success(val answer: AssistantAnswer) : AssistantOutcome
    data class Failure(
        val code: String,
        val message: String,
        val retryable: Boolean = false
    ) : AssistantOutcome
}

interface AssistantCapability {
    val spec: AssistantCapabilitySpec
    suspend fun execute(arguments: JsonObject): AssistantOutcome
}
