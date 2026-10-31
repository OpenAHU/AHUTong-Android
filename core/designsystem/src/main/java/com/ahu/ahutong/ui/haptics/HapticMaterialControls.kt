package com.ahu.ahutong.ui.haptics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.material3.FilledTonalButton as MaterialFilledTonalButton
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.Checkbox as MaterialCheckbox
import androidx.compose.material3.RadioButton as MaterialRadioButton
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** Same Material controls and defaults; only accepted callbacks gain the shared haptic policy. */
@Composable
fun HapticButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = MaterialButton(rememberHapticAction(action = onClick), modifier, enabled, shape, colors,
    elevation, border, contentPadding, interactionSource, content)

@Composable
fun HapticTextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = MaterialTextButton(rememberHapticAction(action = onClick), modifier, enabled, shape, colors,
    elevation, border, contentPadding, interactionSource, content)

@Composable
fun HapticFilledTonalButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(), border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = MaterialFilledTonalButton(rememberHapticAction(action = onClick), modifier, enabled, shape, colors,
    elevation, border, contentPadding, interactionSource, content)

@Composable
fun HapticOutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = MaterialOutlinedButton(rememberHapticAction(action = onClick), modifier, enabled, shape, colors,
    elevation, border, contentPadding, interactionSource, content)

@Composable
fun HapticIconButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) = MaterialIconButton(onClick = rememberHapticAction(action = onClick), modifier = modifier,
    enabled = enabled, colors = colors, interactionSource = interactionSource, content = content)

@Composable
fun HapticCheckbox(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    enabled: Boolean = true, colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
) {
    val feedback = rememberAppHapticFeedback()
    MaterialCheckbox(checked, onCheckedChange?.let { action -> { next ->
        if (enabled && next != checked) feedback.perform(AppHapticEvent.Selection)
        action(next)
    } }, modifier, enabled, colors, interactionSource)
}

@Composable
fun HapticRadioButton(
    selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier,
    enabled: Boolean = true, colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
) {
    val feedback = rememberAppHapticFeedback()
    MaterialRadioButton(selected, onClick?.let { action -> {
        if (enabled && !selected) feedback.perform(AppHapticEvent.Selection)
        action()
    } }, modifier, enabled, colors, interactionSource)
}

@Composable
fun HapticSwitch(
    checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null, enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(), interactionSource: MutableInteractionSource? = null
) {
    val feedback = rememberAppHapticFeedback()
    MaterialSwitch(checked, onCheckedChange?.let { action -> { next ->
        if (enabled && next != checked) feedback.perform(if (next) AppHapticEvent.ToggleOn else AppHapticEvent.ToggleOff)
        action(next)
    } }, modifier, thumbContent, enabled, colors, interactionSource)
}
