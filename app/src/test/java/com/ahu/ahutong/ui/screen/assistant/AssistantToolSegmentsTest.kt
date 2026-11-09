package com.ahu.ahutong.ui.screen.assistant

import com.ahu.ahutong.assistant.AssistantServiceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantToolSegmentsTest {

    @Test
    fun `tool before reply stays before it`() {
        val tool = tool("tool-1")
        val reply = AssistantChatItem.Assistant("reply", "题干如下", streaming = false)

        val segments = toolSegments(listOf(tool, reply))

        assertEquals(2, segments.size)
        assertNull(segments[0].assistant)
        assertEquals(listOf(tool), segments[0].tools)
        assertEquals(reply, segments[1].assistant)
    }

    @Test
    fun `tool after text stays with the text that introduced it`() {
        val text = AssistantChatItem.Assistant("text", "我先查一下", streaming = false)
        val tool = tool("tool-1")
        val reply = AssistantChatItem.Assistant("reply", "查到了", streaming = false)

        val segments = toolSegments(listOf(text, tool, reply))

        assertEquals(2, segments.size)
        assertEquals(text, segments[0].assistant)
        assertEquals(listOf(tool), segments[0].tools)
        assertEquals(reply, segments[1].assistant)
    }

    @Test
    fun `reasoning before tool stays above it`() {
        val reasoning = AssistantChatItem.Reasoning("reasoning", "需要取题", streaming = false)
        val tool = tool("tool-1")
        val reply = AssistantChatItem.Assistant("reply", "题干如下", streaming = false)

        val segments = toolSegments(listOf(reasoning, tool, reply))

        assertEquals(2, segments.size)
        assertEquals(listOf(reasoning), segments[0].reasonings)
        assertEquals(listOf(tool), segments[0].tools)
        assertEquals(reply, segments[1].assistant)
    }

    private fun tool(id: String) = AssistantChatItem.Tool(
        id = id,
        event = AssistantServiceEvent.ToolResult(
            toolId = "get_work_detail",
            title = "查看作业题目",
            ok = true,
            summary = "《第四章（习题2）》共 7 题",
        ),
    )
}
