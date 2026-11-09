package com.ahu.ahutong.assistant.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantThinkingTest {

    @Test
    fun `detects provider from base url and model`() {
        assertEquals(ThinkingProvider.DEEPSEEK, detectThinkingProvider("https://api.deepseek.com/v1", "deepseek-v4.1-flash"))
        assertEquals(ThinkingProvider.KIMI, detectThinkingProvider("https://api.moonshot.cn/v1", "kimi-k3"))
        assertEquals(ThinkingProvider.QWEN, detectThinkingProvider("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.8-max"))
        assertEquals(ThinkingProvider.GLM, detectThinkingProvider("https://open.bigmodel.cn/api/paas/v4", "glm-5.3"))
        assertEquals(ThinkingProvider.OPENAI, detectThinkingProvider("https://api.openai.com/v1", "gpt-5.5"))
        assertEquals(ThinkingProvider.UNKNOWN, detectThinkingProvider("http://192.168.1.9:11434/v1", "gemma4:e4b"))
    }

    @Test
    fun `deepseek maps off low mid high to nobody low high max`() {
        val off = thinkingPayload(ThinkingProvider.DEEPSEEK, AssistantThinkingLevel.OFF, "deepseek-v4.1-flash")
        assertEquals("disabled", off.getAsJsonObject("thinking").get("type").asString)
        assertFalse(off.has("reasoning_effort"))

        listOf(
            AssistantThinkingLevel.LOW to "low",
            AssistantThinkingLevel.MID to "high",
            AssistantThinkingLevel.HIGH to "max",
        ).forEach { (level, expected) ->
            val body = thinkingPayload(ThinkingProvider.DEEPSEEK, level, "deepseek-v4.1-flash")
            assertEquals("enabled", body.getAsJsonObject("thinking").get("type").asString)
            assertEquals(expected, body.get("reasoning_effort").asString)
        }
    }

    @Test
    fun `openai and qwen use their own effort fields`() {
        assertEquals(
            "none",
            thinkingPayload(ThinkingProvider.OPENAI, AssistantThinkingLevel.OFF, "gpt-5.5")
                .get("reasoning_effort").asString,
        )
        assertEquals(
            "medium",
            thinkingPayload(ThinkingProvider.OPENAI, AssistantThinkingLevel.MID, "gpt-5.5")
                .get("reasoning_effort").asString,
        )

        val qwenOff = thinkingPayload(ThinkingProvider.QWEN, AssistantThinkingLevel.OFF, "qwen3.8-max")
        assertFalse(qwenOff.get("enable_thinking").asBoolean)
        assertFalse(qwenOff.has("reasoning_effort"))

        val qwenHigh = thinkingPayload(ThinkingProvider.QWEN, AssistantThinkingLevel.HIGH, "qwen3.8-max")
        assertTrue(qwenHigh.get("enable_thinking").asBoolean)
        assertEquals("xhigh", qwenHigh.get("reasoning_effort").asString)
    }

    @Test
    fun `kimi k3 always thinks and only takes effort`() {
        val off = thinkingPayload(ThinkingProvider.KIMI, AssistantThinkingLevel.OFF, "kimi-k3")
        assertFalse(off.has("thinking"))
        assertEquals("low", off.get("reasoning_effort").asString)

        val high = thinkingPayload(ThinkingProvider.KIMI, AssistantThinkingLevel.HIGH, "kimi-k3")
        assertEquals("max", high.get("reasoning_effort").asString)

        val k26 = thinkingPayload(ThinkingProvider.KIMI, AssistantThinkingLevel.OFF, "kimi-k2.6")
        assertEquals("disabled", k26.getAsJsonObject("thinking").get("type").asString)
    }

    @Test
    fun `glm maps mid to high and high to max`() {
        assertEquals(
            "high",
            thinkingPayload(ThinkingProvider.GLM, AssistantThinkingLevel.MID, "glm-5.3")
                .get("reasoning_effort").asString,
        )
        assertEquals(
            "max",
            thinkingPayload(ThinkingProvider.GLM, AssistantThinkingLevel.HIGH, "glm-5.3")
                .get("reasoning_effort").asString,
        )
    }

    @Test
    fun `unknown provider sends nothing`() {
        assertEquals(
            0,
            thinkingPayload(ThinkingProvider.UNKNOWN, AssistantThinkingLevel.HIGH, "mystery-model").size(),
        )
    }

    @Test
    fun `capability guesses keep local ollama tags text only`() {
        assertFalse(guessSupportsThinking("http://127.0.0.1:11434/v1", "gemma4:e4b"))
        assertFalse(guessSupportsVision("http://127.0.0.1:11434/v1", "gemma4:e4b"))
        assertTrue(guessSupportsThinking("https://api.deepseek.com/v1", "deepseek-v4.1-flash"))
        assertTrue(guessSupportsVision("https://api.deepseek.com/v1", "deepseek-v4.1-flash"))
        assertFalse(guessSupportsVision("https://api.deepseek.com/v1", "deepseek-v4-pro"))
        assertTrue(guessSupportsVision("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3-vl-max"))
    }
}
