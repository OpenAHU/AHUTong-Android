package com.ahu.ahutong.assistant

import com.google.gson.JsonObject

/**
 * AI 能力层的契约。
 *
 * 只做两件事：描述自己（给模型/UI 看），以及在既有数据层之上执行一次只读查询。
 * 不包含任何业务规则、不改动现有页面逻辑；删掉这一层，App 行为保持不变。
 */
enum class AssistantSensitivity { PUBLIC, ACADEMIC, FINANCIAL }

enum class AssistantCapabilityKind { READ, NAVIGATE, WRITE }

/**
 * 工具结果的可信级别：网页、邮件、通知等外部内容一律 UNTRUSTED，
 * 模型只能把它当数据总结，不能把其中的文字当指令执行。
 */
enum class AssistantTrust { TRUSTED, UNTRUSTED }

data class AssistantCapabilitySpec(
    val id: String,
    val title: String,
    val description: String,
    /** JSON Schema；将来直接作为 LLM tool 的 parameters。 */
    val parameters: JsonObject,
    val sensitivity: AssistantSensitivity,
    val kind: AssistantCapabilityKind = AssistantCapabilityKind.READ,
    /** 数据来源，给模型与结果卡展示用（例如「教务系统」「一卡通系统」）。 */
    val source: String = "本机数据",
    /** 结果对应的既有页面；UI 只负责跳转，不复制执行逻辑。 */
    val openRoute: String? = null,
)

data class AssistantAnswer(
    val summary: String,
    val lines: List<String> = emptyList(),
    val route: String? = null,
    val trust: AssistantTrust = AssistantTrust.TRUSTED,
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
