package com.ahu.ahutong.assistant

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCapabilityRegistryTest {

    private class FakeCapability(
        override val spec: AssistantCapabilitySpec
    ) : AssistantCapability {
        override suspend fun execute(arguments: JsonObject): AssistantOutcome =
            AssistantOutcome.Success(AssistantAnswer("ok"))
    }

    private fun spec(id: String) = AssistantCapabilitySpec(
        id = id,
        title = id,
        description = "desc-$id",
        parameters = JsonParser.parseString("""{"type":"object","properties":{}}""").asJsonObject,
        sensitivity = AssistantSensitivity.PUBLIC,
    )

    @Test
    fun findsCapabilityById() {
        val registry = AssistantCapabilityRegistry(listOf(FakeCapability(spec("a")), FakeCapability(spec("b"))))
        assertEquals("a", registry.find("a")?.spec?.id)
        assertEquals(null, registry.find("missing"))
    }

    @Test
    fun exportsOpenAiToolSchemas() {
        val registry = AssistantCapabilityRegistry(listOf(FakeCapability(spec("free_classroom"))))
        val schema = registry.toolSchemas().toString()
        assertTrue(schema.contains("\"type\":\"function\""))
        assertTrue(schema.contains("\"name\":\"free_classroom\""))
        assertTrue(schema.contains("\"parameters\""))
    }
}
