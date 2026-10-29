package com.ahu.ahutong.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ahu.ahutong.ui.theme.pack.LocalComponentPack
import com.kyant.backdrop.Backdrop

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
    LocalComponentPack.current.Slider(
        value.coerceIn(valueRange), onValueChange, modifier, valueRange, onValueChangeFinished, backdrop
    )
}
