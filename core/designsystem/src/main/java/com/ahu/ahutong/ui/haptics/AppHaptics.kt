package com.ahu.ahutong.ui.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
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
            else fallback.performHapticFeedback(if (event == AppHapticEvent.LongPress) {
                HapticFeedbackType.LongPress
            } else HapticFeedbackType.TextHandleMove)
        }
    }
}

@Composable
fun rememberHapticAction(
    event: AppHapticEvent = AppHapticEvent.Click,
    action: () -> Unit
): () -> Unit {
    val feedback = rememberAppHapticFeedback()
    return { feedback.perform(event); action() }
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

/** Uses public, manufacturer-calibrated primitives; no assumptions about motor type or frequency. */
private class AndroidHapticController(private val context: Context) {
    private val rateLimiter = HapticRateLimiter()
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)
    }.getOrNull()
    // Android 11's primitive support query is unreliable; advanced effects require Android 12+.
    private val supportsPrimitives = Build.VERSION.SDK_INT >= 31 && runCatching {
        vibrator?.let { it.hasVibrator() && it.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK) } == true
    }.getOrDefault(false)

    fun perform(event: AppHapticEvent, view: View) {
        if (!view.isAttachedToWindow || !view.hasWindowFocus() || !view.isShown || !view.isHapticFeedbackEnabled) return
        val enabled = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0
        }.getOrDefault(false)
        if (!rateLimiter.accept(SystemClock.elapsedRealtimeNanos(), enabled)) return
        val primitive = when (event) {
            AppHapticEvent.Selection, AppHapticEvent.ToggleOff -> VibrationEffect.Composition.PRIMITIVE_TICK
            AppHapticEvent.Click, AppHapticEvent.ToggleOn -> VibrationEffect.Composition.PRIMITIVE_CLICK
            else -> null
        }
        if (supportsPrimitives && primitive != null && Build.VERSION.SDK_INT >= 31) {
            val scale = if (event == AppHapticEvent.ToggleOn || event == AppHapticEvent.ToggleOff) 0.7f else 0.5f
            if (runCatching { playPrimitive(primitive, scale) }.isSuccess) return
        }
        // Semantic constants include platform fallbacks and preserve the device's long-press/result feel.
        runCatching { view.performHapticFeedback(event.platformConstant()) }
    }

    @RequiresApi(31)
    private fun playPrimitive(primitive: Int, scale: Float) {
        val effect = VibrationEffect.startComposition().addPrimitive(primitive, scale).compose()
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator?.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build())
        } else {
            vibrator?.vibrate(effect, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
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
    AppHapticEvent.LongPress -> HapticFeedbackConstants.LONG_PRESS
    AppHapticEvent.Confirm -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.CONTEXT_CLICK
    AppHapticEvent.Reject -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
}
