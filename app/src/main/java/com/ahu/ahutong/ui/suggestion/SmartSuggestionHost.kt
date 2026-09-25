package com.ahu.ahutong.ui.suggestion

import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.ui.SuggestionPolicy
import com.ahu.ahutong.personalization.runtime.PredictionUiState
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.utils.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.opacity
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.launch

@Composable
fun SmartSuggestionHost(
    runtime: BehaviorPredictionRuntime,
    backdrop: Backdrop,
    blocked: Boolean,
    hiddenForDiagnostics: Boolean = false,
    bottomSpacing: Dp,
    onSuggestionClick: (PredictionUiState.Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by runtime.uiState.collectAsState()
    LaunchedEffect(blocked) {
        runtime.setSuggestionHostBlocked(blocked)
    }
    if (blocked || hiddenForDiagnostics) return
    val suggestion = state as? PredictionUiState.Suggestion ?: return
    val suggestionRoute = com.ahu.ahutong.personalization.action.AppActionCatalog.spec(suggestion.action).route
    if (!com.ahu.ahutong.data.dao.AHUCache.canOpenRoute(suggestionRoute)) return
    val density = LocalDensity.current
    val navigationBarInset = WindowInsets.navigationBars.getBottom(density)
    val horizontalOffsetPx = with(density) { 12.dp.roundToPx() }
    val verticalOffsetPx = with(density) {
        bottomSpacing.roundToPx() + navigationBarInset
    }

    SuggestionApplicationWindow(
        suggestion = suggestion,
        runtime = runtime,
        backdrop = backdrop,
        horizontalOffsetPx = horizontalOffsetPx,
        verticalOffsetPx = verticalOffsetPx,
        onSuggestionClick = onSuggestionClick,
        modifier = modifier
    )
}

@Composable
private fun SuggestionApplicationWindow(
    suggestion: PredictionUiState.Suggestion,
    runtime: BehaviorPredictionRuntime,
    backdrop: Backdrop,
    horizontalOffsetPx: Int,
    verticalOffsetPx: Int,
    onSuggestionClick: (PredictionUiState.Suggestion) -> Unit,
    modifier: Modifier
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as DialogWindowProvider).window
        var windowAttached by remember(suggestion.executionId) {
            mutableStateOf(dialogView.isAttachedToWindow)
        }
        var bubbleLaidOut by remember(suggestion.executionId) { mutableStateOf(false) }

        SideEffect {
            configureSuggestionWindow(
                window = dialogWindow,
                horizontalOffsetPx = horizontalOffsetPx,
                verticalOffsetPx = verticalOffsetPx
            )
        }
        DisposableEffect(dialogView) {
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    windowAttached = true
                }

                override fun onViewDetachedFromWindow(view: View) {
                    windowAttached = false
                }
            }
            dialogView.addOnAttachStateChangeListener(listener)
            windowAttached = dialogView.isAttachedToWindow
            onDispose { dialogView.removeOnAttachStateChangeListener(listener) }
        }

        SuggestionBubbleContent(
            suggestion = suggestion,
            runtime = runtime,
            backdrop = backdrop,
            onSuggestionClick = onSuggestionClick,
            onLaidOut = { bubbleLaidOut = true },
            modifier = modifier
        )

        LaunchedEffect(
            suggestion.executionId,
            windowAttached,
            bubbleLaidOut,
            suggestion.exposureConfirmed
        ) {
            if (windowAttached && bubbleLaidOut && !suggestion.exposureConfirmed) {
                withFrameNanos { }
                if (dialogView.isAttachedToWindow && dialogView.isShown &&
                    dialogView.windowVisibility == View.VISIBLE
                ) {
                    runtime.confirmSuggestionVisible(suggestion.executionId)
                }
            }
        }
    }
}

private fun configureSuggestionWindow(
    window: Window,
    horizontalOffsetPx: Int,
    verticalOffsetPx: Int
) {
    window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.addFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    )
    window.setDimAmount(0f)
    window.setGravity(Gravity.END or Gravity.BOTTOM)
    window.attributes = window.attributes.apply {
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.END or Gravity.BOTTOM
        x = horizontalOffsetPx
        y = verticalOffsetPx
        dimAmount = 0f
    }
}

@Composable
private fun SuggestionBubbleContent(
    suggestion: PredictionUiState.Suggestion,
    runtime: BehaviorPredictionRuntime,
    backdrop: Backdrop,
    onSuggestionClick: (PredictionUiState.Suggestion) -> Unit,
    onLaidOut: () -> Unit,
    modifier: Modifier
) {
    val suggestionShape = ContinuousCapsule
    val lifetimeOpacity = remember(suggestion.executionId) { Animatable(1f) }
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope, suggestion.executionId) {
        InteractiveHighlight(
            animationScope = animationScope,
            highlightRadiusMultiplier = 0.9f,
            fixedHighlightPressProgress = 0.8f,
            drawGlobalPressOverlay = false,
            holdHighlightPositionOnRelease = true,
            positionReturnDampingRatio = 0.75f,
            releaseAnimationDurationMillis = 120,
            onPressStart = {
                runtime.pauseSuggestionVisibility(suggestion.executionId)
                animationScope.launch { lifetimeOpacity.snapTo(1f) }
            },
            onPressEnd = {
                runtime.restartSuggestionVisibility(suggestion.executionId)
            }
        )
    }
    val isLiquidGlass = LocalIsLiquidGlassEnabled.current
    val glassContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.64f)
    val accentContentColor = MaterialTheme.colorScheme.primary.copy(alpha = lifetimeOpacity.value)
    val primaryContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = lifetimeOpacity.value)
    val secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = lifetimeOpacity.value)

    LaunchedEffect(
        suggestion.executionId,
        suggestion.exposureConfirmed,
        suggestion.shownAtElapsedMs,
        suggestion.expiresAtElapsedMs,
        suggestion.visibilityPaused
    ) {
        if (!suggestion.exposureConfirmed) {
            lifetimeOpacity.snapTo(1f)
            return@LaunchedEffect
        }
        if (suggestion.visibilityPaused) {
            lifetimeOpacity.snapTo(1f)
            return@LaunchedEffect
        }
        val now = SystemClock.elapsedRealtime()
        val remainingDurationMs = (suggestion.expiresAtElapsedMs - now).coerceAtLeast(0L)
        lifetimeOpacity.snapTo(
            SuggestionPolicy.remainingVisibilityFraction(
                shownAtElapsedMs = suggestion.shownAtElapsedMs,
                expiresAtElapsedMs = suggestion.expiresAtElapsedMs,
                nowElapsedMs = now
            )
        )
        if (remainingDurationMs > 0L) {
            lifetimeOpacity.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = remainingDurationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    easing = LinearEasing
                )
            )
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                if (coordinates.size.width > 0 && coordinates.size.height > 0) {
                    onLaidOut()
                }
            }
            .then(
                if (isLiquidGlass) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { suggestionShape },
                        effects = {
                            vibrancy()
                            blur(8f.dp.toPx())
                            lens(24f.dp.toPx(), 24f.dp.toPx())
                            opacity(lifetimeOpacity.value)
                        },
                        highlight = { Highlight.Default.copy(alpha = lifetimeOpacity.value) },
                        shadow = { Shadow(alpha = lifetimeOpacity.value) },
                        layerBlock = {
                            val width = size.width
                            val height = size.height
                            val progress = interactiveHighlight.pressProgress
                            val scale = lerp(1f, 1f + 4f.dp.toPx() / height, progress)
                            val maxOffset = size.minDimension
                            val offset = interactiveHighlight.offset
                            translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                            translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                            val maxDragScale = 4f.dp.toPx() / height
                            val offsetAngle = atan2(offset.y, offset.x)
                            scaleX = scale +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (width / height).fastCoerceAtMost(1f)
                            scaleY = scale +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (height / width).fastCoerceAtMost(1f)
                        },
                        onDrawSurface = {
                            drawRect(
                                glassContainerColor.copy(
                                    alpha = glassContainerColor.alpha * lifetimeOpacity.value
                                )
                            )
                        }
                    )
                } else {
                    Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = suggestionShape,
                            ambientColor = Color.Black.copy(alpha = 0.12f * lifetimeOpacity.value),
                            spotColor = Color.Black.copy(alpha = 0.12f * lifetimeOpacity.value)
                        )
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                                alpha = lifetimeOpacity.value
                            ),
                            shape = suggestionShape
                        )
                }
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = { onSuggestionClick(suggestion) }
            )
            .semantics { contentDescription = "猜你想用：${suggestion.title}" }
            .clip(suggestionShape)
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
    ) {
        Row(
            modifier = Modifier.padding(
                start = 12.dp,
                top = 8.dp,
                bottom = 8.dp,
                end = 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = accentContentColor
            )
            Column {
                Text(
                    "猜你想用 · ${suggestion.title}",
                    color = primaryContentColor,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    suggestion.reason,
                    color = secondaryContentColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = runtime::dismissSuggestionByUser) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭建议",
                    tint = secondaryContentColor
                )
            }
        }
    }
}
