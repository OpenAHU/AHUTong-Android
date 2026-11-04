package com.ahu.ahutong.assistant

import com.google.gson.JsonObject

data class AssistantTurn(
    val question: String,
    val capabilityId: String?,
    val outcome: AssistantOutcome?,
)

/** 一次问答：先由确定性识别选能力，再交给能力执行。 */
class AssistantService(
    private val registry: AssistantCapabilityRegistry,
    private val parse: (String) -> AssistantIntent = AssistantIntentParser::parse,
) {
    suspend fun ask(question: String): AssistantTurn {
        val capabilityId = parse(question).capabilityId
            ?: return AssistantTurn(question, null, null)
        val capability = registry.find(capabilityId)
            ?: return AssistantTurn(question, null, null)
        return AssistantTurn(question, capabilityId, capability.execute(JsonObject()))
    }
}
