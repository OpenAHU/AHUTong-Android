package com.ahu.ahutong.ui.haptics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HapticPolicyTest {
    @Test
    fun disabledSystemFeedbackNeverConsumesTheNextEnabledInteraction() {
        val limiter = HapticRateLimiter()
        assertFalse(limiter.accept(0, enabled = false))
        assertTrue(limiter.accept(1, enabled = true))
        assertFalse(limiter.accept(60_000_000, enabled = false))
        assertTrue(limiter.accept(60_000_001, enabled = true))
    }

    @Test
    fun nestedComponentCallbacksAndRapidFramesProduceOnePulse() {
        val limiter = HapticRateLimiter()
        assertTrue(limiter.accept(0))
        assertFalse(limiter.accept(0))
        assertFalse(limiter.accept(16_000_000))
        assertFalse(limiter.accept(49_999_999))
        assertTrue(limiter.accept(50_000_000))
        assertTrue(limiter.accept(500_000_000))
    }

    @Test
    fun sliderDetentsDependOnPositionInsteadOfFrameRateOrRangeUnits() {
        assertEquals(0, hapticSliderDetent(-1f, 0f..1f))
        assertEquals(10, hapticSliderDetent(0.5f, 0f..1f))
        assertEquals(10, hapticSliderDetent(50f, 0f..100f))
        assertEquals(hapticSliderDetent(0.501f, 0f..1f), hapticSliderDetent(0.51f, 0f..1f))
        assertEquals(20, hapticSliderDetent(2f, 0f..1f))
        assertNull(hapticSliderDetent(Float.NaN, 0f..1f))
        assertNull(hapticSliderDetent(1f, 1f..1f))
    }
}
