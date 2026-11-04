package com.ahu.ahutong.assistant.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantStreamAssemblerTest {

    @Test
    fun accumulatesTextAndToolArgumentsAcrossChunks() {
        val assembler = AssistantStreamAssembler()

        assertEquals("你", assembler.accept("""{"choices":[{"delta":{"content":"你"}}]}"""))
        assertEquals("好", assembler.accept("""{"choices":[{"delta":{"content":"好"}}]}"""))
        assembler.accept(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","function":{"name":"free_classroom","arguments":"{\"cam"}}]}}]}"""
        )
        assembler.accept(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"pusId\":1}"}}]}}]}"""
        )

        val reply = assembler.reply()
        assertEquals("你好", reply.content)
        assertEquals(1, reply.toolCalls.size)
        val call = reply.toolCalls.single()
        assertEquals("call-1", call.id)
        assertEquals("free_classroom", call.name)
        assertEquals(1, call.arguments.get("campusId").asInt)
    }

    @Test
    fun ignoresMalformedChunks() {
        val assembler = AssistantStreamAssembler()
        assertEquals(null, assembler.accept("not-json"))
        assertEquals(null, assembler.reply().content)
    }
}
