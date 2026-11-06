package com.ahu.ahutong.ui.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

fun interface AppHapticFeedback {
    fun perform(event: AppHapticEvent)
}

private val LocalHapticController = staticCompositionLocalOf<AndroidHapticController?> { null }
internal val LocalAppHapticsEnabled = staticCompositionLocalOf { false }

/** Reuses one controller while binding each window's feedback to its own visible View. */
@Composable
fun AppHapticFeedbackProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val inherited = LocalHapticController.current
    val controller = inherited ?: remember(context.applicationContext) {
        AndroidHapticController(context.applicationContext)
    }
    val view = LocalView.current
    val enabled = rememberUpdatedState(LocalAppHapticsEnabled.current)
    val composeFeedback = remember(controller, view) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (enabled.value) controller.perform(hapticFeedbackType.toAppEvent(), view)
            }
        }
    }
    CompositionLocalProvider(
        LocalHapticController provides controller,
        LocalHapticFeedback provides composeFeedback,
        content = content
    )
}

@Composable
fun rememberAppHapticFeedback(): AppHapticFeedback {
    val controller = LocalHapticController.current
    val view = LocalView.current
    val fallback = LocalHapticFeedback.current
    val enabled = rememberUpdatedState(LocalAppHapticsEnabled.current)
    return remember(controller, view, fallback) {
        AppHapticFeedback { event ->
            if (!enabled.value) return@AppHapticFeedback
            if (controller != null) controller.perform(event, view)
            else if (event != AppHapticEvent.Collapse) {
                fallback.performHapticFeedback(when (event) {
                    AppHapticEvent.LongPress -> HapticFeedbackType.LongPress
                    AppHapticEvent.Confirm -> HapticFeedbackType.Confirm
                    AppHapticEvent.Reject -> HapticFeedbackType.Reject
                    AppHapticEvent.ToggleOn -> HapticFeedbackType.ToggleOn
                    AppHapticEvent.ToggleOff -> HapticFeedbackType.ToggleOff
                    else -> HapticFeedbackType.TextHandleMove
                })
            }
        }
    }
}

@Composable
fun rememberHapticAction(
    event: AppHapticEvent = AppHapticEvent.Click,
    action: () -> Unit
): () -> Unit {
    val feedback = rememberAppHapticFeedback()
    return { performHapticAction(event, feedback::perform, action) }
}

private fun HapticFeedbackType.toAppEvent(): AppHapticEvent = when (this) {
    HapticFeedbackType.LongPress -> AppHapticEvent.LongPress
    HapticFeedbackType.Confirm -> AppHapticEvent.Confirm
    HapticFeedbackType.Reject -> AppHapticEvent.Reject
    HapticFeedbackType.ToggleOn -> AppHapticEvent.ToggleOn
    HapticFeedbackType.ToggleOff -> AppHapticEvent.ToggleOff
    HapticFeedbackType.TextHandleMove,
    HapticFeedbackType.SegmentTick,
    HapticFeedbackType.SegmentFrequentTick -> AppHapticEvent.Selection
    else -> AppHapticEvent.Click
}

/** Uses public, manufacturer-calibrated feedback events without assuming a motor type or frequency. */
private class AndroidHapticController(private val context: Context) {
    private val rateLimiter = HapticRateLimiter()

    fun perform(event: AppHapticEvent, view: View) {
        if (!view.isAttachedToWindow || !view.hasWindowFocus() || !view.isShown || !view.isHapticFeedbackEnabled) return
        val enabled = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0
        }.getOrDefault(false)
        if (!rateLimiter.accept(SystemClock.elapsedRealtimeNanos(), enabled)) return
        // A quiet close still consumes this interaction so an outer button cannot add a click.
        if (event == AppHapticEvent.Collapse) return
        // Keep OEM-calibrated on/off, selection and result effects instead of replacing them with a click.
        runCatching { view.performHapticFeedback(event.platformConstant()) }
    }
}

private fun AppHapticEvent.platformConstant(): Int = when (this) {
    AppHapticEvent.Click -> HapticFeedbackConstants.CONTEXT_CLICK
    AppHapticEvent.Selection -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    AppHapticEvent.ToggleOn -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON
        else HapticFeedbackConstants.CONTEXT_CLICK
    AppHapticEvent.ToggleOff -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_OFF
        else HapticFeedbackConstants.CLOCK_TICK
    AppHapticEvent.Expand -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START
        else HapticFeedbackConstants.VIRTUAL_KEY
    AppHapticEvent.Collapse -> HapticFeedbackConstants.CONTEXT_CLICK // Consumed silently before dispatch.
    AppHapticEvent.LongPress -> HapticFeedbackConstants.LONG_PRESS
    AppHapticEvent.Confirm -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.CONTEXT_CLICK
    AppHapticEvent.Reject -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
}
