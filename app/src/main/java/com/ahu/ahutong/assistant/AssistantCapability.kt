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
    /** 写操作是否必须先让用户确认。 */
    val requiresConfirmation: Boolean = kind == AssistantCapabilityKind.WRITE,
    /** 是否需要在确认卡里采集校园卡支付密码。 */
    val requiresPaymentPassword: Boolean = false,
)

data class AssistantAnswer(
    val summary: String,
    val lines: List<String> = emptyList(),
    val route: String? = null,
    val trust: AssistantTrust = AssistantTrust.TRUSTED,
    /** 对话页工具卡的结构化数据；为空时卡片退化为 summary + lines。 */
    val card: AssistantCard? = null,
)

enum class AssistantCardTone { NEUTRAL, ACCENT, SUCCESS, DANGER }

data class AssistantCardStat(val label: String, val value: String)

data class AssistantCardItem(
    val title: String,
    val subtitle: String? = null,
    val value: String? = null,
    val status: String? = null,
    val tone: AssistantCardTone = AssistantCardTone.NEUTRAL,
    /** 同一组的条目前面显示一次分组标题（例如日期）。 */
    val group: String? = null,
)

/**
 * 工具结果卡：只描述"怎么展示"，不参与业务判断。
 * kind 用于给个别卡片做更贴合的排版，其余字段通用。
 */
data class AssistantCard(
    val kind: String,
    val headline: String? = null,
    val headlinePrefix: String? = null,
    val headlineSuffix: String? = null,
    val subtitle: String? = null,
    val stats: List<AssistantCardStat> = emptyList(),
    val items: List<AssistantCardItem> = emptyList(),
    val note: String? = null,
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

    /** 需要本机支付密码的能力覆盖它；密码只在本次调用内使用。 */
    suspend fun executeWithSecret(arguments: JsonObject, secret: String?): AssistantOutcome =
        execute(arguments)

    /** 确认卡里补充展示的业务信息，例如电费房间；不要在这里发起扣款。 */
    suspend fun describe(arguments: JsonObject): String? = null
}
