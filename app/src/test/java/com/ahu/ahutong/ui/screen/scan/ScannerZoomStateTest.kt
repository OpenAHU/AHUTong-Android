package com.ahu.ahutong.ui.screen.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScannerZoomStateTest {
    @Test
    fun presetsContainOnlyZoomRatiosSupportedByTheBoundCamera() {
        assertEquals(listOf(0.5f, 1f, 2f), ScannerZoomState(1f, 0.5f, 10f).presets)
        assertEquals(listOf(1f), ScannerZoomState(1f, 1f, 1.8f).presets)
        assertEquals(listOf(2f), ScannerZoomState(1.2f, 1.2f, 5f).presets)
        assertEquals(listOf(3f, 5f), ScannerZoomState(3f, 3f, 5f).presets)
        assertTrue(ScannerZoomState(1f, 0.5f, 10f).isSupported)
        assertFalse(ScannerZoomState().isSupported)
    }

    @Test
    fun requestedPresetAndPinchZoomStayWithinTheCameraRange() {
        val state = ScannerZoomState(2f, 0.5f, 8f)
        assertEquals(0.5f, state.clamp(0.1f))
        assertEquals(8f, state.clamp(20f))
        assertEquals(3f, state.scaledBy(1.5f))
        assertEquals(0.5f, state.scaledBy(0.01f))
        assertEquals(8f, state.scaledBy(Float.MAX_VALUE))
    }

    @Test
    fun malformedGestureFactorsAndNonFiniteRequestsDoNotChangeTheCurrentZoom() {
        val state = ScannerZoomState(2f, 0.5f, 8f)
        for (factor in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
            assertEquals(2f, state.scaledBy(factor))
        }
        assertEquals(2f, state.clamp(Float.NaN))
        assertEquals(1f, ScannerZoomState(1f, Float.NaN, 8f).clamp(2f))
        assertFalse(ScannerZoomState(1f, 2f, 1f).isSupported)
    }

    @Test
    fun ratioLabelsAreCompactAndIndependentOfDeviceLocale() {
        assertEquals("0.5×", scannerZoomLabel(0.5f))
        assertEquals("1×", scannerZoomLabel(1f))
        assertEquals("2×", scannerZoomLabel(2f))
        assertEquals("1.3×", scannerZoomLabel(1.26f))
    }
}
