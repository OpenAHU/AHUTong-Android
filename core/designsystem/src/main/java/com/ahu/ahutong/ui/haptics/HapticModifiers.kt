package com.ahu.ahutong.ui.haptics

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.foundation.combinedClickable as foundationCombinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable as foundationSelectable
import androidx.compose.foundation.selection.toggleable as foundationToggleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

/** Preserve Foundation gesture/semantics handling; feedback follows an accepted action, never a raw touch. */
fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val feedback = rememberAppHapticFeedback()
    this.foundationClickable(enabled, onClickLabel, role) {
        if (enabled) feedback.perform(AppHapticEvent.Click)
        onClick()
    }
}

fun Modifier.hapticClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val feedback = rememberAppHapticFeedback()
    this.foundationClickable(interactionSource, indication, enabled, onClickLabel, role) {
        if (enabled) feedback.perform(AppHapticEvent.Click)
        onClick()
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.hapticCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    hapticFeedbackEnabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val feedback = rememberAppHapticFeedback()
    this.foundationCombinedClickable(
        enabled = enabled, onClickLabel = onClickLabel, role = role,
        onLongClickLabel = onLongClickLabel, hapticFeedbackEnabled = hapticFeedbackEnabled,
        onLongClick = onLongClick?.let { action -> {
            if (enabled && hapticFeedbackEnabled) feedback.perform(AppHapticEvent.LongPress)
            action()
        } },
        onDoubleClick = onDoubleClick?.let { action -> {
            if (enabled) feedback.perform(AppHapticEvent.Click)
            action()
        } },
        onClick = { if (enabled) feedback.perform(AppHapticEvent.Click); onClick() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.hapticCombinedClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    hapticFeedbackEnabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val feedback = rememberAppHapticFeedback()
    this.foundationCombinedClickable(
        interactionSource = interactionSource, indication = indication,
        enabled = enabled, onClickLabel = onClickLabel, role = role,
        onLongClickLabel = onLongClickLabel, hapticFeedbackEnabled = hapticFeedbackEnabled,
        onLongClick = onLongClick?.let { action -> {
            if (enabled && hapticFeedbackEnabled) feedback.perform(AppHapticEvent.LongPress)
            action()
        } },
        onDoubleClick = onDoubleClick?.let { action -> {
            if (enabled) feedback.perform(AppHapticEvent.Click)
            action()
        } },
        onClick = { if (enabled) feedback.perform(AppHapticEvent.Click); onClick() }
    )
}

fun Modifier.hapticSelectable(
    selected: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val feedback = rememberAppHapticFeedback()
    this.foundationSelectable(selected, enabled, role) {
        if (enabled && !selected) feedback.perform(AppHapticEvent.Selection)
        onClick()
    }
}

fun Modifier.hapticSelectable(
    selected: Boolean,
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val feedback = rememberAppHapticFeedback()
    this.foundationSelectable(selected, interactionSource, indication, enabled, role) {
        if (enabled && !selected) feedback.perform(AppHapticEvent.Selection)
        onClick()
    }
}

fun Modifier.hapticToggleable(
    value: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    onValueChange: (Boolean) -> Unit
): Modifier = composed {
    val feedback = rememberAppHapticFeedback()
    this.foundationToggleable(value, enabled, role) { next ->
        if (enabled && next != value) feedback.perform(toggleEvent(next, role))
        onValueChange(next)
    }
}

fun Modifier.hapticToggleable(
    value: Boolean,
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    role: Role? = null,
    onValueChange: (Boolean) -> Unit
): Modifier = composed {
    val feedback = rememberAppHapticFeedback()
    this.foundationToggleable(value, interactionSource, indication, enabled, role) { next ->
        if (enabled && next != value) feedback.perform(toggleEvent(next, role))
        onValueChange(next)
    }
}

private fun toggleEvent(selected: Boolean, role: Role?): AppHapticEvent = when {
    role == Role.Checkbox -> AppHapticEvent.Selection
    selected -> AppHapticEvent.ToggleOn
    else -> AppHapticEvent.ToggleOff
}
