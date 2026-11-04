package com.ahu.ahutong.assistant

import com.ahu.ahutong.assistant.model.AssistantChatModel
import com.ahu.ahutong.assistant.model.AssistantModelConfigStore
import com.ahu.ahutong.assistant.model.OpenAiCompatibleChatModel
import com.ahu.ahutong.assistant.safety.AssistantApprovalGate
import kotlinx.coroutines.flow.collect

data class AssistantTurn(
    val question: String,
    val capabilityId: String?,
    val outcome: AssistantOutcome?,
)

/**
 * 一次问答：
 * - 配置了模型时走 [AssistantRuntime]（工具循环），模型失败则回退到确定性路由；
 * - 未配置模型时完全本地，不发任何外部请求。
 */
class AssistantService(
    private val registry: AssistantCapabilityRegistry,
    private val parse: (String) -> AssistantIntent = AssistantIntentParser::parse,
    private val modelFactory: () -> AssistantChatModel? = DEFAULT_MODEL_FACTORY,
) {
    suspend fun ask(question: String): AssistantTurn {
        val model = runCatching { modelFactory() }.getOrNull()
        if (model != null) {
            val modelTurn = askWithModel(question, model)
            if (modelTurn.outcome is AssistantOutcome.Success) return modelTurn
            val fallback = askDeterministic(question)
            if (fallback.outcome != null) return fallback
            return modelTurn
        }
        return askDeterministic(question)
    }

    private suspend fun askWithModel(question: String, model: AssistantChatModel): AssistantTurn {
        val runtime = AssistantRuntime(model, registry)
        val gate = AssistantApprovalGate()
        var answer: String? = null
        var route: String? = null
        var failure: String? = null
        runtime.run(emptyList(), question, gate).collect { event ->
            when (event) {
                is AssistantRuntimeEvent.AssistantMessage -> answer = event.text
                is AssistantRuntimeEvent.ToolResultEvent ->
                    if (event.ok && event.route != null) route = event.route
                is AssistantRuntimeEvent.ApprovalRequired -> gate.reject()
                is AssistantRuntimeEvent.Failed -> failure = event.message
                else -> Unit
            }
        }
        val outcome = if (failure != null && answer == null) {
            AssistantOutcome.Failure("model", failure.orEmpty())
        } else {
            AssistantOutcome.Success(
                AssistantAnswer(
                    summary = answer?.takeIf { it.isNotBlank() } ?: "我没有拿到可用结果。",
                    route = route,
                )
            )
        }
        return AssistantTurn(question, null, outcome)
    }

    private suspend fun askDeterministic(question: String): AssistantTurn {
        val intent = parse(question)
        val capabilityId = intent.capabilityId
            ?: return AssistantTurn(question, null, null)
        val capability = registry.find(capabilityId)
            ?: return AssistantTurn(question, null, null)
        return AssistantTurn(question, capabilityId, capability.execute(intent.arguments))
    }

    private companion object {
        val DEFAULT_MODEL_FACTORY: () -> AssistantChatModel? = {
            AssistantModelConfigStore.load()?.let { OpenAiCompatibleChatModel(it) }
        }
    }
}
