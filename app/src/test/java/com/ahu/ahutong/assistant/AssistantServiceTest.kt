package com.ahu.ahutong.assistant

import com.ahu.ahutong.assistant.model.AssistantChatModel
import com.ahu.ahutong.assistant.model.AssistantMessage
import com.ahu.ahutong.assistant.model.AssistantModelReply
import com.ahu.ahutong.assistant.model.AssistantToolCall
import com.ahu.ahutong.assistant.model.AssistantToolSpec
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantServiceTest {

    private class FakeCapability(
        override val spec: AssistantCapabilitySpec,
        private val answer: String,
    ) : AssistantCapability {
        override suspend fun execute(arguments: JsonObject): AssistantOutcome =
            AssistantOutcome.Success(AssistantAnswer(answer, route = spec.openRoute))
    }

    private fun spec(id: String, route: String? = null) = AssistantCapabilitySpec(
        id = id,
        title = id,
        description = id,
        parameters = JsonParser.parseString("""{"type":"object","properties":{}}""").asJsonObject,
        sensitivity = AssistantSensitivity.PUBLIC,
        openRoute = route,
    )

    private fun registry() = AssistantCapabilityRegistry(
        listOf(
            FakeCapability(spec("get_phone_book", route = "phone_book"), "教务处 6386"),
            FakeCapability(spec("get_balance"), "余额 10 元"),
        )
    )

    private class SequenceModel(private val replies: List<AssistantModelReply>) : AssistantChatModel {
        override val name = "fake"
        private var calls = 0

        override suspend fun complete(
            messages: List<AssistantMessage>,
            tools: List<AssistantToolSpec>,
        ): AssistantModelReply {
            val index = calls.coerceAtMost(replies.lastIndex)
            calls++
            return replies[index]
        }
    }

    @Test
    fun modelCallsToolsAndKeepsThePageRoute() = runBlocking {
        val model = SequenceModel(
            listOf(
                AssistantModelReply(
                    toolCalls = listOf(
                        AssistantToolCall(
                            id = "call-1",
                            name = "get_phone_book",
                            arguments = JsonObject().apply { addProperty("query", "教务处") },
                        )
                    )
                ),
                AssistantModelReply(content = "教务处的电话是 6386"),
            )
        )
        val service = AssistantService(registry(), modelFactory = { model })
        val turn = service.ask("查教务处电话")
        val outcome = turn.outcome as AssistantOutcome.Success
        assertEquals("教务处的电话是 6386", outcome.answer.summary)
        assertEquals("phone_book", outcome.answer.route)
    }

    @Test
    fun modelFailureSurfacesAnErrorInsteadOfSilentlyFallingBack() = runBlocking {
        val broken = object : AssistantChatModel {
            override val name = "broken"
            override suspend fun complete(
                messages: List<AssistantMessage>,
                tools: List<AssistantToolSpec>,
            ): AssistantModelReply = throw IOException("network down")
        }
        val service = AssistantService(registry(), modelFactory = { broken })
        val turn = service.ask("校园卡余额还有多少")
        val outcome = turn.outcome as AssistantOutcome.Failure
        assertTrue(outcome.message.contains("network down"))
    }

    @Test
    fun streamEmitsToolCardAndStatusForLocalRouting() = runBlocking {
        val service = AssistantService(registry(), modelFactory = { null })
        val events = mutableListOf<AssistantServiceEvent>()
        service.stream("查教务处电话").collect { events += it }
        assertTrue(events.any { it is AssistantServiceEvent.Status })
        assertTrue(events.any { it is AssistantServiceEvent.ToolResult })
        assertTrue(events.any { it is AssistantServiceEvent.AssistantMessage })
        assertTrue(events.last() is AssistantServiceEvent.Done)
    }

    @Test
    fun noModelKeepsEverythingLocal() = runBlocking {
        val service = AssistantService(registry(), modelFactory = { null })
        val turn = service.ask("查教务处电话")
        assertEquals("get_phone_book", turn.capabilityId)
        assertTrue(turn.outcome is AssistantOutcome.Success)
    }
}
