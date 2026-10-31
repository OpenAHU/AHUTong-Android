package com.ahu.ahutong.ui.haptics

import kotlin.math.roundToInt

enum class AppHapticEvent { Click, Selection, ToggleOn, ToggleOff, LongPress, Confirm, Reject }

/** Shared by all component themes and nested dialogs; duplicate callbacks produce one pulse. */
internal class HapticRateLimiter {
    private var lastFeedbackNanos: Long? = null

    fun accept(nowNanos: Long, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        if (lastFeedbackNanos?.let { nowNanos - it in 0L until 50_000_000L } == true) return false
        lastFeedbackNanos = nowNanos
        return true
    }
}

/** Continuous sliders have twenty tactile detents, independently of their numeric range. */
internal fun hapticSliderDetent(value: Float, range: ClosedFloatingPointRange<Float>): Int? {
    val extent = range.endInclusive - range.start
    if (!value.isFinite() || !extent.isFinite() || extent <= 0f) return null
    return (((value - range.start) / extent).coerceIn(0f, 1f) * 20).roundToInt()
}
