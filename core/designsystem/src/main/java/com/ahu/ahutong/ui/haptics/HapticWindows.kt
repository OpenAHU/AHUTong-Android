package com.ahu.ahutong.ui.haptics

import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup as ComposePopup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Composable
fun HapticDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) = ComposeDialog(onDismissRequest, properties) { AppHapticFeedbackProvider(content) }

/** Each Material slot is composed inside the dialog window, so its LocalView is the focused one. */
@Composable
fun HapticAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) = MaterialAlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = { AppHapticFeedbackProvider(confirmButton) },
    modifier = modifier,
    dismissButton = dismissButton?.let { slot -> { AppHapticFeedbackProvider(slot) } },
    icon = icon?.let { slot -> { AppHapticFeedbackProvider(slot) } },
    title = title?.let { slot -> { AppHapticFeedbackProvider(slot) } },
    text = text?.let { slot -> { AppHapticFeedbackProvider(slot) } },
    shape = shape, containerColor = containerColor, iconContentColor = iconContentColor,
    titleContentColor = titleContentColor, textContentColor = textContentColor,
    tonalElevation = tonalElevation, properties = properties
)

@Composable
fun HapticPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit
) = ComposePopup(popupPositionProvider, onDismissRequest, properties) { AppHapticFeedbackProvider(content) }
