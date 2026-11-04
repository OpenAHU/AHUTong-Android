package com.ahu.ahutong.assistant

import com.ahu.ahutong.assistant.model.AssistantChatModel
import com.ahu.ahutong.assistant.model.AssistantModelConfigStore
import com.ahu.ahutong.assistant.model.OpenAiCompatibleChatModel
import com.ahu.ahutong.assistant.safety.AssistantApprovalGate
import com.ahu.ahutong.assistant.safety.AssistantApprovalRequest
import com.ahu.ahutong.assistant.safety.AssistantToolPolicy
import com.ahu.ahutong.assistant.safety.AssistantWriteOutcome
import com.ahu.ahutong.assistant.safety.InMemoryAssistantWriteJournal
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
    data class AssistantMessage(val text: String) : AssistantServiceEvent
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
    data class Failed(val message: String) : AssistantServiceEvent
    data object Done : AssistantServiceEvent
}

/**
 * 一次问答：
 * - 配置了模型时走 [AssistantRuntime]（工具循环 + 流式文本）；
 * - 未配置模型时完全本地，仍然产出同样的工具卡/状态/审批事件。
 */
class AssistantService(
    private val registry: AssistantCapabilityRegistry,
    private val parse: (String) -> AssistantIntent = AssistantIntentParser::parse,
    private val modelFactory: () -> AssistantChatModel? = DEFAULT_MODEL_FACTORY,
) {
    private val writeJournal = InMemoryAssistantWriteJournal()

    fun stream(
        question: String,
        gate: AssistantApprovalGate = AssistantApprovalGate(),
    ): Flow<AssistantServiceEvent> = flow {
        val model = runCatching { modelFactory() }.getOrNull()
        if (model != null) {
            emitModel(question, model, gate)
        } else {
            emitLocal(question, gate)
        }
    }

    private suspend fun FlowCollector<AssistantServiceEvent>.emitModel(
        question: String,
        model: AssistantChatModel,
        gate: AssistantApprovalGate,
    ) {
        val soul = AssistantPreferences.soulFor(AssistantPreferences.personality())
        val prompt = buildString {
            append(AssistantRuntime.DEFAULT_SYSTEM_PROMPT.trimEnd())
            if (soul.isNotBlank()) {
                appendLine()
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
        runtime.run(emptyList(), question, gate).collect { event ->
            when (event) {
                is AssistantRuntimeEvent.Status -> emit(AssistantServiceEvent.Status(event.text))
                AssistantRuntimeEvent.AssistantStart -> emit(AssistantServiceEvent.AssistantStart)
                is AssistantRuntimeEvent.AssistantDelta -> emit(AssistantServiceEvent.AssistantDelta(event.text))
                is AssistantRuntimeEvent.AssistantMessage -> emit(AssistantServiceEvent.AssistantMessage(event.text))
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
                is AssistantRuntimeEvent.Failed -> emit(AssistantServiceEvent.Failed(event.message))
                AssistantRuntimeEvent.Done -> {
                    done = true
                    emit(AssistantServiceEvent.Done)
                }
            }
        }
        if (!done) emit(AssistantServiceEvent.Done)
    }

    private suspend fun FlowCollector<AssistantServiceEvent>.emitLocal(
        question: String,
        gate: AssistantApprovalGate,
    ) {
        val intent = parse(question)
        val capability = intent.capabilityId?.let(registry::find)
        if (capability == null) {
            emit(AssistantServiceEvent.AssistantMessage(HELP_TEXT))
            emit(AssistantServiceEvent.Done)
            return
        }
        val isWrite = capability.spec.kind == AssistantCapabilityKind.WRITE
        var secret: String? = null

        if (isWrite) {
            val blocked = writeJournal.check(capability.spec.id, intent.arguments)
            if (blocked != null) {
                emit(
                    AssistantServiceEvent.ToolResult(
                        toolId = capability.spec.id,
                        title = capability.spec.title,
                        ok = false,
                        summary = blocked,
                        source = capability.spec.source,
                    )
                )
                emit(AssistantServiceEvent.AssistantMessage(blocked))
                emit(AssistantServiceEvent.Done)
                return
            }
            val deferred = gate.prepare()
            val detail = runCatching { capability.describe(intent.arguments) }.getOrNull()
            emit(
                AssistantServiceEvent.ApprovalRequired(
                    AssistantApprovalRequest(
                        id = UUID.randomUUID().toString(),
                        toolId = capability.spec.id,
                        title = capability.spec.title,
                        summary = buildString {
                            append(capability.spec.title)
                            if (intent.arguments.size() > 0) {
                                append("：")
                                append(intent.arguments.entrySet().joinToString("，") { "${it.key}=${it.value}" })
                            }
                            detail?.takeIf { it.isNotBlank() }?.let {
                                appendLine()
                                append(it)
                            }
                        },
                        risk = AssistantToolPolicy.riskText(capability.spec),
                        arguments = intent.arguments,
                        requiresPaymentPassword = capability.spec.requiresPaymentPassword,
                    )
                )
            )
            val decision = deferred.await()
            if (!decision.approved) {
                emit(
                    AssistantServiceEvent.ToolResult(
                        toolId = capability.spec.id,
                        title = capability.spec.title,
                        ok = false,
                        summary = "已取消",
                        source = capability.spec.source,
                    )
                )
                emit(AssistantServiceEvent.AssistantMessage("已取消这次操作。"))
                emit(AssistantServiceEvent.Done)
                return
            }
            secret = decision.paymentPassword
            writeJournal.begin(capability.spec.id, intent.arguments)
        }

        emit(AssistantServiceEvent.Status("正在执行：${capability.spec.title}…"))
        val outcome = try {
            if (isWrite) {
                capability.executeWithSecret(intent.arguments, secret)
            } else {
                capability.execute(intent.arguments)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AssistantOutcome.Failure("exception", error.message ?: "执行失败")
        }
        if (isWrite) {
            val writeOutcome = when (outcome) {
                is AssistantOutcome.Success -> AssistantWriteOutcome.SUCCESS
                is AssistantOutcome.Failure ->
                    if (outcome.code == "network" || outcome.code == "timeout") {
                        AssistantWriteOutcome.UNKNOWN
                    } else {
                        AssistantWriteOutcome.KNOWN_FAILURE
                    }
            }
            writeJournal.finish(capability.spec.id, intent.arguments, writeOutcome)
        }

        when (outcome) {
            is AssistantOutcome.Success -> {
                emit(
                    AssistantServiceEvent.ToolResult(
                        toolId = capability.spec.id,
                        title = capability.spec.title,
                        ok = true,
                        summary = outcome.answer.summary,
                        card = outcome.answer.card,
                        route = outcome.answer.route ?: capability.spec.openRoute,
                        source = capability.spec.source,
                        trust = outcome.answer.trust,
                        navigateOnly = capability.spec.kind == AssistantCapabilityKind.NAVIGATE,
                    )
                )
                emit(AssistantServiceEvent.AssistantMessage(outcome.answer.summary))
            }
            is AssistantOutcome.Failure -> {
                emit(
                    AssistantServiceEvent.ToolResult(
                        toolId = capability.spec.id,
                        title = capability.spec.title,
                        ok = false,
                        summary = outcome.message,
                        source = capability.spec.source,
                    )
                )
                emit(AssistantServiceEvent.Failed(outcome.message))
            }
        }
        emit(AssistantServiceEvent.Done)
    }

    /** 兼容旧调用：把事件流压成一次结果；写操作在无人确认的调用里自动取消。 */
    suspend fun ask(question: String): AssistantTurn {
        var text: String? = null
        var failure: String? = null
        var route: String? = null
        val gate = AssistantApprovalGate()
        stream(question, gate).collect { event ->
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
        return AssistantTurn(question, parse(question).capabilityId, outcome)
    }

    private companion object {
        val DEFAULT_MODEL_FACTORY: () -> AssistantChatModel? = {
            AssistantModelConfigStore.load()?.let { OpenAiCompatibleChatModel(it) }
        }

        const val HELP_TEXT =
            "我可以查课表、空教室、成绩、绩点排名、考试、余额、账单、电费、考勤、培养方案、评教、" +
                "通知、失物招领、邮箱、电话本、作业、章节进度、天气和必吃榜；" +
                "充值、评教提交、发邮件等会先弹确认卡，确认后再执行。"
    }
}
