package com.ahu.ahutong.assistant.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantMessageJsonTest {

    @Test
    fun `user message with images becomes content parts`() {
        val message = AssistantMessage(
            role = "user",
            content = "随附题目图片",
            images = listOf(
                AssistantImage("data:image/png;base64,AAAA", "第1题公式"),
                AssistantImage("data:image/jpeg;base64,BBBB", "第3题图片"),
            ),
        )

        val json = message.toJson()
        val content = json.getAsJsonArray("content")

        assertEquals(3, content.size())
        assertEquals("text", content[0].asJsonObject.get("type").asString)
        assertEquals("随附题目图片", content[0].asJsonObject.get("text").asString)
        assertEquals("image_url", content[1].asJsonObject.get("type").asString)
        assertEquals(
            "data:image/png;base64,AAAA",
            content[1].asJsonObject.getAsJsonObject("image_url").get("url").asString,
        )
        assertEquals(
            "data:image/jpeg;base64,BBBB",
            content[2].asJsonObject.getAsJsonObject("image_url").get("url").asString,
        )
    }

    @Test
    fun `tool message keeps plain string content`() {
        val message = AssistantMessage(
            role = "tool",
            content = """{"ok":true}""",
            toolCallId = "call-1",
        )

        val json = message.toJson()

        assertEquals("""{"ok":true}""", json.get("content").asString)
    }
}
