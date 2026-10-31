package com.ahu.ahutong.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ahu.ahutong.ui.theme.pack.LocalComponentPack
import com.kyant.backdrop.Backdrop
import com.ahu.ahutong.ui.haptics.AppHapticEvent
import com.ahu.ahutong.ui.haptics.hapticSliderDetent
import com.ahu.ahutong.ui.haptics.rememberAppHapticFeedback

/** Sliders use the selected theme's native control, including inside a mixed component pack. */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: () -> Unit = {},
    backdrop: Backdrop? = null
) {
    val feedback = rememberAppHapticFeedback()
    var previousDetent by remember(valueRange) { mutableStateOf(hapticSliderDetent(value, valueRange)) }
    var changedByUser by remember { mutableStateOf(false) }
    LocalComponentPack.current.Slider(
        value.coerceIn(valueRange), { next ->
            val detent = hapticSliderDetent(next, valueRange)
            if (detent != null && detent != previousDetent) feedback.perform(AppHapticEvent.Selection)
            previousDetent = detent
            changedByUser = true
            onValueChange(next)
        }, modifier, valueRange, {
            if (changedByUser) feedback.perform(AppHapticEvent.Click)
            changedByUser = false
            onValueChangeFinished()
        }, backdrop
    )
}
