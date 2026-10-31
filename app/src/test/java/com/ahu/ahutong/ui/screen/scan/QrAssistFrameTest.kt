package com.ahu.ahutong.ui.screen.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QrAssistFrameTest {
    @Test
    fun centralDarkCodeAreaIsNotHiddenByABrightBackground() {
        val data = ByteArray(80 * 60) { 255.toByte() }
        for (y in 15 until 45) for (x in 20 until 60) data[y * 80 + x] = 12
        assertEquals(QrLuminanceStats(12f, 1f, 0f), sampleQrLighting(data, 80, 60))
    }

    @Test
    fun unsignedHighlightsAndTinyFramesAreSampledCorrectly() {
        assertEquals(QrLuminanceStats(255f, 0f, 1f), sampleQrLighting(byteArrayOf(-1), 1, 1))
        assertEquals(QrLuminanceStats(128f, 0f, 0f), sampleQrLighting(ByteArray(8) { 128.toByte() }, 4, 2))
        assertFailsWith<IllegalArgumentException> { sampleQrLighting(ByteArray(3), 2, 2) }
    }
}
