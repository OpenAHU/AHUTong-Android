package com.ahu.ahutong.assistant

import com.ahu.ahutong.assistant.model.AssistantChatModel
import com.ahu.ahutong.assistant.model.AssistantImage
import com.ahu.ahutong.assistant.model.AssistantMessage
import com.ahu.ahutong.assistant.model.AssistantModelEvent
import com.ahu.ahutong.assistant.model.AssistantModelReply
import com.ahu.ahutong.assistant.model.AssistantToolCall
import com.ahu.ahutong.assistant.model.AssistantToolSpec
import com.ahu.ahutong.assistant.safety.AssistantApprovalGate
import com.ahu.ahutong.assistant.safety.AssistantWriteJournal
import com.ahu.ahutong.assistant.safety.InMemoryAssistantWriteJournal
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRuntimeTest {

    private class FakeModel(replies: List<AssistantModelReply>) : AssistantChatModel {
        private val queue = ArrayDeque(replies)
        override val name: String = "fake"
        override suspend fun complete(
            messages: List<AssistantMessage>,
            tools: List<AssistantToolSpec>,
        ): AssistantModelReply = queue.removeFirst()

        override fun stream(
            messages: List<AssistantMessage>,
            tools: List<AssistantToolSpec>,
        ): Flow<AssistantModelEvent> = flow {
            emit(AssistantModelEvent.Completed(queue.removeFirst()))
        }
    }

    private class CapturingModel(
        replies: List<AssistantModelReply>,
        override val supportsVision: Boolean = true,
    ) : AssistantChatModel {
        private val queue = ArrayDeque(replies)
        val calls = mutableListOf<List<AssistantMessage>>()
        override val name: String = "capture"

        override suspend fun complete(
            messages: List<AssistantMessage>,
            tools: List<AssistantToolSpec>,
        ): AssistantModelReply = queue.removeFirst()

        override fun stream(
            messages: List<AssistantMessage>,
            tools: List<AssistantToolSpec>,
        ): Flow<AssistantModelEvent> = flow {
            calls += messages.toList()
            emit(AssistantModelEvent.Completed(queue.removeFirst()))
        }
    }

    private class FakeCapability(
        override val spec: AssistantCapabilitySpec,
        private val result: AssistantOutcome,
    ) : AssistantCapability {
        var executions = 0
        override suspend fun execute(arguments: JsonObject): AssistantOutcome {
            executions++
            return result
        }
    }

    private fun spec(kind: AssistantCapabilityKind = AssistantCapabilityKind.READ) =
        AssistantCapabilitySpec(
            id = "echo",
            title = "回显",
            description = "测试用",
            parameters = JsonParser.parseString("""{"type":"object"}""").asJsonObject,
            sensitivity = AssistantSensitivity.PUBLIC,
            kind = kind,
        )

    private fun call() = AssistantToolCall(
        id = "call-1",
        name = "echo",
        arguments = JsonObject(),
    )

    @Test
    fun executesReadToolThenAnswers() = runBlocking {
        val capability = FakeCapability(
            spec = spec(),
            result = AssistantOutcome.Success(AssistantAnswer("今天有 1 节课")),
        )
        val model = FakeModel(
            listOf(
                AssistantModelReply(toolCalls = listOf(call())),
                AssistantModelReply(content = "今天有 1 节课。"),
            )
        )
        val runtime = AssistantRuntime(
            model = model,
            registry = AssistantCapabilityRegistry(listOf(capability)),
        )
        val events = mutableListOf<AssistantRuntimeEvent>()
        runtime.run(emptyList(), "今天有什么课", AssistantApprovalGate()).collect { events += it }

        assertEquals(1, capability.executions)
        assertTrue(events.toString(), events.any { it is AssistantRuntimeEvent.ToolResultEvent && it.ok })
        assertTrue(events.toString(), events.any { it is AssistantRuntimeEvent.AssistantMessage && it.text.contains("1 节课") })
        assertTrue(events.toString(), events.last() is AssistantRuntimeEvent.Done)
    }

    @Test
    fun `tool images are attached after all tool messages`() = runBlocking {
        val capability = FakeCapability(
            spec = spec(),
            result = AssistantOutcome.Success(
                AssistantAnswer(
                    "共 1 题",
                    images = listOf(AssistantImage("data:image/png;base64,AAAA", "第1题公式")),
                )
            ),
        )
        val model = CapturingModel(
            listOf(
                AssistantModelReply(toolCalls = listOf(call())),
                AssistantModelReply(content = "答案是……"),
            )
        )
        val runtime = AssistantRuntime(
            model = model,
            registry = AssistantCapabilityRegistry(listOf(capability)),
        )
        runtime.run(emptyList(), "帮我看看这道题", AssistantApprovalGate()).collect { }

        val secondCall = model.calls[1]
        val toolIndex = secondCall.indexOfFirst { it.role == "tool" }
        val imageIndex = secondCall.indexOfFirst { it.role == "user" && it.images.isNotEmpty() }
        assertTrue(toolIndex >= 0)
        assertEquals(toolIndex + 1, imageIndex)
        assertEquals(1, secondCall[imageIndex].images.size)
        assertTrue(secondCall[imageIndex].content.orEmpty().contains("随附图片"))
    }

    @Test
    fun `tool images are not attached when model has no vision`() = runBlocking {
        val capability = FakeCapability(
            spec = spec(),
            result = AssistantOutcome.Success(
                AssistantAnswer(
                    "共 1 题",
                    images = listOf(AssistantImage("data:image/png;base64,AAAA", "第1题公式")),
                )
            ),
        )
        val model = CapturingModel(
            listOf(
                AssistantModelReply(toolCalls = listOf(call())),
                AssistantModelReply(content = "我看不到图片。"),
            ),
            supportsVision = false,
        )
        val runtime = AssistantRuntime(
            model = model,
            registry = AssistantCapabilityRegistry(listOf(capability)),
        )
        runtime.run(emptyList(), "帮我看看这道题", AssistantApprovalGate()).collect { }

        val secondCall = model.calls[1]
        assertTrue(secondCall.none { it.images.isNotEmpty() })
    }

    @Test
    fun `read tool requiring confirmation pauses and stays out of the timeline`() = runBlocking {
        val capability = FakeCapability(
            spec = spec().copy(
                requiresConfirmation = true,
                confirmationAction = "继续解答",
                silent = true,
            ),
            result = AssistantOutcome.Success(AssistantAnswer("用户已确认继续解答")),
        )
        val model = FakeModel(
            listOf(
                AssistantModelReply(toolCalls = listOf(call())),
                AssistantModelReply(content = "解答如下。"),
            )
        )
        val runtime = AssistantRuntime(
            model = model,
            registry = AssistantCapabilityRegistry(listOf(capability)),
        )
        val gate = AssistantApprovalGate()
        var confirmAction: String? = null
        val events = mutableListOf<AssistantRuntimeEvent>()
        runtime.run(emptyList(), "直接给我答案", gate).collect { event ->
            events += event
            if (event is AssistantRuntimeEvent.ApprovalRequired) {
                confirmAction = event.request.confirmationAction
                gate.approve()
            }
        }

        assertEquals("继续解答", confirmAction)
        assertEquals(1, capability.executions)
        assertTrue(events.none { it is AssistantRuntimeEvent.ToolResultEvent })
        assertTrue(events.any { it is AssistantRuntimeEvent.AssistantMessage && it.text.contains("解答如下") })
    }

    @Test
    fun standardPromptCarriesToneGuards() {
        val prompt = AssistantRuntime.DEFAULT_SYSTEM_PROMPT
        assertTrue(prompt.contains("不卖萌、不冷淡"))
        assertTrue(prompt.contains("不使用 emoji"))
        assertTrue(prompt.contains("不敢乱发挥"))
    }

    @Test
    fun writeToolWaitsForApprovalAndStopsOnReject() = runBlocking {
        val capability = FakeCapability(
            spec = spec(kind = AssistantCapabilityKind.WRITE),
            result = AssistantOutcome.Success(AssistantAnswer("已充值")),
        )
        val model = FakeModel(
            listOf(
                AssistantModelReply(toolCalls = listOf(call())),
                AssistantModelReply(content = "已取消。"),
            )
        )
        val journal = InMemoryAssistantWriteJournal()
        val runtime = AssistantRuntime(
            model = model,
            registry = AssistantCapabilityRegistry(listOf(capability)),
            writeJournal = journal,
        )
        var approvalRequested = false
        val gate = AssistantApprovalGate()
        runtime.run(emptyList(), "帮我充值", gate).collect { event ->
            if (event is AssistantRuntimeEvent.ApprovalRequired) {
                approvalRequested = true
                gate.reject()
            }
        }
        assertTrue(approvalRequested)
        assertEquals(0, capability.executions)
    }
}
