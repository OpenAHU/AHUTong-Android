package com.ahu.ahutong.ui.screen.assistant

import com.ahu.ahutong.assistant.AssistantServiceEvent
import com.ahu.ahutong.assistant.safety.AssistantApprovalRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `round notices survive approval split and reset on next user`() {
        val user1 = AssistantChatItem.User("u1", "看看作业")
        val fetch = tool("tool-1")
        val approval = AssistantChatItem.Approval(
            id = "approval-1",
            request = AssistantApprovalRequest(
                id = "request-1",
                toolId = "confirm_homework_help",
                title = "解题确认",
                summary = "解题确认",
                risk = "确认后 AI 才会给出解答",
            ),
        )
        val answer = AssistantChatItem.Assistant(
            id = "answer-1",
            text = "解答……",
            academicAnswer = true,
        )
        val user2 = AssistantChatItem.User("u2", "谢谢")

        val notices = buildRoundNotices(listOf(user1, fetch, approval, answer, user2))

        assertTrue(notices.getValue("u1").usedWorkDetail)
        assertTrue(notices.getValue("u1").academicAnswer)
        assertFalse(notices.getValue("u2").usedWorkDetail)
        assertFalse(notices.getValue("u2").academicAnswer)
    }

    @Test
    fun `failed fetch does not trigger integrity notice`() {
        val user = AssistantChatItem.User("u1", "看看作业")
        val failed = AssistantChatItem.Tool(
            id = "tool-1",
            event = AssistantServiceEvent.ToolResult(
                toolId = "get_work_detail",
                title = "查看作业题目",
                ok = false,
                summary = "读取失败",
            ),
        )

        val notices = buildRoundNotices(listOf(user, failed))

        assertFalse(notices.getValue("u1").usedWorkDetail)
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
