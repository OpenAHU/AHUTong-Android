package com.ahu.ahutong.assistant.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantStreamAssemblerTest {

    @Test
    fun accumulatesTextAndToolArgumentsAcrossChunks() {
        val assembler = AssistantStreamAssembler()

        assertEquals("你", assembler.accept("""{"choices":[{"delta":{"content":"你"}}]}""")?.content)
        assertEquals("好", assembler.accept("""{"choices":[{"delta":{"content":"好"}}]}""")?.content)
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
    fun capturesUsageFromFinalStreamChunk() {
        val assembler = AssistantStreamAssembler()
        assembler.accept("""{"choices":[{"delta":{"content":"好"}}]}""")
        assembler.accept(
            """{"choices":[],"usage":{"prompt_tokens":120,"completion_tokens":30,"total_tokens":150,"prompt_cache_hit_tokens":20}}"""
        )

        val usage = assembler.reply().usage
        assertEquals(120, usage?.promptTokens)
        assertEquals(30, usage?.completionTokens)
        assertEquals(150, usage?.totalTokens)
        assertEquals(20, usage?.cachedTokens)
    }

    @Test
    fun separatesReasoningFromAnswer() {
        val assembler = AssistantStreamAssembler()
        val reasoning = assembler.accept("""{"choices":[{"delta":{"reasoning_content":"先看课表"}}]}""")
        assertEquals("先看课表", reasoning?.reasoning)
        assertEquals(null, reasoning?.content)

        val answer = assembler.accept("""{"choices":[{"delta":{"content":"今天有课"}}]}""")
        assertEquals("今天有课", answer?.content)
        assertEquals(null, answer?.reasoning)

        val reply = assembler.reply()
        assertEquals("先看课表", reply.reasoning)
        assertEquals("今天有课", reply.content)
    }

    @Test
    fun ignoresMalformedChunks() {
        val assembler = AssistantStreamAssembler()
        assertEquals(null, assembler.accept("not-json"))
        assertEquals(null, assembler.reply().content)
    }
}
