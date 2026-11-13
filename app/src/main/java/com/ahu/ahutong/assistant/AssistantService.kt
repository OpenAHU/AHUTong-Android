package com.ahu.ahutong.assistant

import com.ahu.ahutong.assistant.model.AssistantChatModel
import com.ahu.ahutong.assistant.model.AssistantMessage
import com.ahu.ahutong.assistant.model.AssistantModelConfigStore
import com.ahu.ahutong.assistant.model.OpenAiCompatibleChatModel
import com.ahu.ahutong.assistant.safety.AssistantApprovalGate
import com.ahu.ahutong.assistant.safety.AssistantApprovalRequest
import com.ahu.ahutong.assistant.safety.AssistantWriteJournal
import com.ahu.ahutong.assistant.safety.InMemoryAssistantWriteJournal
import com.ahu.ahutong.assistant.safety.PersistentAssistantWriteJournal
import com.ahu.ahutong.assistant.safety.content.AssistantContentGate
import com.ahu.ahutong.assistant.safety.content.ContentVerdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

data class AssistantTurn(
    val question: String,
    val capabilityId: String?,
    val outcome: AssistantOutcome?,
)

/** 对话页消费的事件流；与 NEXT 的 AgentEvent 一一对应。 */
sealed interface AssistantServiceEvent {
    data class Status(val text: String) : AssistantServiceEvent
    data object AssistantStart : AssistantServiceEvent
    data class AssistantDelta(val text: String) : AssistantServiceEvent
    data class ReasoningDelta(val text: String) : AssistantServiceEvent
    data class AssistantMessage(val text: String) : AssistantServiceEvent
    data class ContentBlocked(
        val text: String,
        val category: String,
        val terminate: Boolean = false,
    ) : AssistantServiceEvent
    data class ToolResult(
        val toolId: String,
        val title: String,
        val ok: Boolean,
        val summary: String?,
        val card: AssistantCard? = null,
        val route: String? = null,
        val source: String = "本机数据",
        val fetchedAt: Long = System.currentTimeMillis(),
        val trust: AssistantTrust = AssistantTrust.TRUSTED,
        val navigateOnly: Boolean = false,
    ) : AssistantServiceEvent
    data class ApprovalRequired(val request: AssistantApprovalRequest) : AssistantServiceEvent
    data class Usage(val totalTokens: Int) : AssistantServiceEvent
    data class Failed(val message: String) : AssistantServiceEvent
    data object Done : AssistantServiceEvent
}

/**
 * 对话入口只走模型。没有可用模型时直接提示配置，不再使用关键词路由。
 */
class AssistantService(
    private val registry: AssistantCapabilityRegistry,
    private val modelFactory: () -> AssistantChatModel? = DEFAULT_MODEL_FACTORY,
) {
    private val writeJournal: AssistantWriteJournal = runCatching {
        PersistentAssistantWriteJournal()
    }.getOrElse {
        InMemoryAssistantWriteJournal()
    }

    fun stream(
        question: String,
        history: List<AssistantMessage> = emptyList(),
        gate: AssistantApprovalGate = AssistantApprovalGate(),
    ): Flow<AssistantServiceEvent> = flow {
        val input = AssistantContentGate.inspectInput(AssistantRuntime.truncateUserInput(question))
        if (input is ContentVerdict.Block) {
            emit(AssistantServiceEvent.ContentBlocked(input.message, input.category.id, input.terminate))
            emit(AssistantServiceEvent.Done)
            return@flow
        }
        val model = runCatching { modelFactory() }.getOrNull()
        if (model == null) {
            emit(AssistantServiceEvent.AssistantMessage("请先在 AI 设置中配置模型并测试连通性。"))
            emit(AssistantServiceEvent.Done)
            return@flow
        }
        emitModel(question, history, model, gate, input)
    }

    private suspend fun FlowCollector<AssistantServiceEvent>.emitModel(
        question: String,
        history: List<AssistantMessage>,
        model: AssistantChatModel,
        gate: AssistantApprovalGate,
        inputVerdict: ContentVerdict,
    ) {
        val soul = AssistantPreferences.soulFor(AssistantPreferences.personality())
        val prompt = buildString {
            append(AssistantRuntime.DEFAULT_SYSTEM_PROMPT.trimEnd())
            if (soul.isNotBlank()) {
                appendLine()
                appendLine()
                appendLine("# 人格设定")
                appendLine()
                append(soul)
            }
        }
        val runtime = AssistantRuntime(
            model = model,
            registry = registry,
            systemPrompt = prompt,
            writeJournal = writeJournal,
        )
        var done = false
        runtime.run(history, question, gate, inputVerdict).collect { event ->
            when (event) {
                is AssistantRuntimeEvent.Status -> emit(AssistantServiceEvent.Status(event.text))
                AssistantRuntimeEvent.AssistantStart -> emit(AssistantServiceEvent.AssistantStart)
                is AssistantRuntimeEvent.AssistantDelta -> emit(AssistantServiceEvent.AssistantDelta(event.text))
                is AssistantRuntimeEvent.ReasoningDelta -> emit(AssistantServiceEvent.ReasoningDelta(event.text))
                is AssistantRuntimeEvent.AssistantMessage -> emit(AssistantServiceEvent.AssistantMessage(event.text))
                is AssistantRuntimeEvent.ContentBlocked -> emit(
                    AssistantServiceEvent.ContentBlocked(event.text, event.category, event.terminate)
                )
                is AssistantRuntimeEvent.ToolResultEvent -> emit(
                    AssistantServiceEvent.ToolResult(
                        toolId = event.toolId,
                        title = event.title,
                        ok = event.ok,
                        summary = event.summary,
                        card = event.card,
                        route = event.route,
                        source = event.source,
                        fetchedAt = event.fetchedAt,
                        trust = event.trust,
                        navigateOnly = event.navigateOnly,
                    )
                )
                is AssistantRuntimeEvent.ApprovalRequired -> emit(
                    AssistantServiceEvent.ApprovalRequired(event.request)
                )
                is AssistantRuntimeEvent.Usage -> emit(AssistantServiceEvent.Usage(event.totalTokens))
                is AssistantRuntimeEvent.Failed -> emit(AssistantServiceEvent.Failed(event.message))
                AssistantRuntimeEvent.Done -> {
                    done = true
                    emit(AssistantServiceEvent.Done)
                }
            }
        }
        if (!done) emit(AssistantServiceEvent.Done)
    }

    /** 兼容旧调用；写操作在无人确认的调用里自动取消。 */
    suspend fun ask(question: String): AssistantTurn {
        var text: String? = null
        var failure: String? = null
        var route: String? = null
        val gate = AssistantApprovalGate()
        stream(question, gate = gate).collect { event ->
            when (event) {
                is AssistantServiceEvent.ApprovalRequired -> gate.reject()
                is AssistantServiceEvent.AssistantMessage -> text = event.text
                is AssistantServiceEvent.ToolResult -> if (event.ok) route = event.route ?: route
                is AssistantServiceEvent.Failed -> failure = event.message
                else -> Unit
            }
        }
        val outcome = when {
            failure != null -> AssistantOutcome.Failure("assistant", failure.orEmpty())
            text != null -> AssistantOutcome.Success(
                AssistantAnswer(summary = text.orEmpty(), route = route)
            )
            else -> null
        }
        return AssistantTurn(question, null, outcome)
    }

    private companion object {
        val DEFAULT_MODEL_FACTORY: () -> AssistantChatModel? = {
            AssistantModelConfigStore.load()?.let { OpenAiCompatibleChatModel(it) }
        }
    }
}
