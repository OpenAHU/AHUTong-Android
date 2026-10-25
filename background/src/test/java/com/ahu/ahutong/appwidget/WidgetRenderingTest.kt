package com.ahu.ahutong.appwidget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetRenderingTest {
    @Test
    fun `Glance failure cannot starve the adaptive widget update`() = runBlocking {
        var adaptiveRendered = false
        val failures = mutableListOf<String>()
        assertFalse(renderWidgetStyles(
            glance = { throw IllegalStateException("fixture Glance failure") },
            adaptive = { adaptiveRendered = true },
            onFailure = { style, _ -> failures += style }
        ))
        assertTrue(adaptiveRendered)
        assertEquals(listOf("Glance"), failures)
    }

    @Test
    fun `both failures are observed before the worker retries`() = runBlocking {
        val failures = mutableListOf<String>()
        assertFalse(renderWidgetStyles(
            glance = { throw IllegalStateException("fixture Glance failure") },
            adaptive = { throw IllegalStateException("fixture adaptive failure") },
            onFailure = { style, _ -> failures += style }
        ))
        assertEquals(listOf("Glance", "adaptive"), failures)
    }

    @Test
    fun `replacement cancellation stops rendering rather than becoming a retry`() = runBlocking {
        var adaptiveRendered = false
        var failures = 0
        assertFailsWith<CancellationException> {
            renderWidgetStyles(
                glance = { throw CancellationException("fixture replacement") },
                adaptive = { adaptiveRendered = true },
                onFailure = { _, _ -> failures++ }
            )
        }
        assertFalse(adaptiveRendered)
        assertEquals(0, failures)
    }
}
