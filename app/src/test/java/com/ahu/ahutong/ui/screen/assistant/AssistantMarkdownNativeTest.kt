package com.ahu.ahutong.ui.screen.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMarkdownNativeTest {

    @Test
    fun `dollar block becomes math fence`() {
        val normalized = normalizeBlockMath("前文\n\$\$\nx^2 + y^2\n\$\$\n后文")
        assertTrue(normalized.contains("```math\nx^2 + y^2\n```"))
        assertTrue(normalized.contains("前文"))
        assertTrue(normalized.contains("后文"))
    }

    @Test
    fun `bracket block becomes math fence`() {
        val normalized = normalizeBlockMath("\\[\na = b\n\\]")
        assertTrue(normalized.contains("```math\na = b\n```"))
    }

    @Test
    fun `single line block is normalized`() {
        val normalized = normalizeBlockMath("\$\$a+b\$\$")
        assertEquals("```math\na+b\n```", normalized)
    }

    @Test
    fun `fenced code is untouched`() {
        val markdown = "```\n\$\$not math\$\$\n```"
        assertEquals(markdown, normalizeBlockMath(markdown))
    }
}
