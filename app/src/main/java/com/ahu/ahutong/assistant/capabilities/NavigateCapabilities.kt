package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilityKind
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.google.gson.JsonObject

/**
 * 动作类能力：只把用户带到既有页面，由原页面完成确认与执行。
 *
 * AI 不代扣款、不提交评教、不发邮件；删掉 AI 层后这些页面照旧可用。
 */
class AssistantNavigateCapability(
    override val spec: AssistantCapabilitySpec,
    private val detail: (JsonObject) -> String? = { null },
) : AssistantCapability {

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val extra = detail(arguments)?.takeIf { it.isNotBlank() }
        val summary = buildString {
            append("「")
            append(spec.title)
            append("」需要在原页面确认")
            if (extra != null) {
                append("（")
                append(extra)
                append("）")
            }
            append("，点下方「打开页面」继续。")
        }
        return AssistantOutcome.Success(AssistantAnswer(summary = summary, route = spec.openRoute))
    }
}

internal fun navigateCapabilities(): List<AssistantCapability> = listOf(
    AssistantNavigateCapability(
        spec = AssistantCapabilitySpec(
            id = "add_calendar_event",
            title = "校历",
            description = "打开校历页。安大通没有本地日历写入，日程以校历页面为准。",
            parameters = emptySchema(),
            sensitivity = AssistantSensitivity.PUBLIC,
            kind = AssistantCapabilityKind.NAVIGATE,
            source = "本地校历",
            openRoute = "school_calendar",
        ),
    ),
)
