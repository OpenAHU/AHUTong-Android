package com.ahu.ahutong.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import com.ahu.ahutong.ui.haptics.HapticSwitch as Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.ahu.ahutong.ui.theme.LocalLiquidGlassTokens
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.utils.DampedDragAnimation
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    userInputEnabled: Boolean = true,
    toggleOnTap: Boolean = true,
    contentDescription: String? = null,
    onHorizontalDragActiveChange: (Boolean) -> Unit = {}
) {
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        MiuixSwitch(
            checked = selected(),
            onCheckedChange = onSelect.takeIf { userInputEnabled && toggleOnTap },
            modifier = modifier
                .heightIn(min = 48.dp)
                .then(if (toggleOnTap) Modifier else Modifier.clearAndSetSemantics {}),
            enabled = userInputEnabled
        )
        return
    }
    val tokens = LocalLiquidGlassTokens.current
    if (!tokens.quality.supportsBlur) {
        val colorScheme = MaterialTheme.colorScheme
        val switchColor = SwitchDefaults.colors(
            checkedThumbColor = colorScheme.onPrimary,
            checkedTrackColor = colorScheme.primary,
            checkedBorderColor = colorScheme.primary,
            disabledCheckedThumbColor = colorScheme.onPrimary,
            disabledCheckedTrackColor = colorScheme.primary,
            disabledCheckedBorderColor = colorScheme.primary,
            uncheckedThumbColor = colorScheme.outline,
            uncheckedTrackColor = colorScheme.surfaceContainerHighest,
            uncheckedBorderColor = colorScheme.outline
        )
        Switch(
            checked = selected(),
            onCheckedChange = onSelect.takeIf { userInputEnabled && toggleOnTap },
            modifier = modifier
                .heightIn(min = 48.dp)
                .then(
                    when {
                        !toggleOnTap -> Modifier.clearAndSetSemantics {}
                        contentDescription != null -> Modifier.semantics {
                            this.contentDescription = contentDescription
                        }
                        else -> Modifier
                    }
                ),
            colors = switchColor
        )
        return
    }

    val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    val currentSelected = rememberUpdatedState(selected)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val currentOnHorizontalDragActiveChange =
        rememberUpdatedState(onHorizontalDragActiveChange)
    var accumulatedDrag by remember { mutableStateOf(Offset.Zero) }
    var gestureMoved by remember { mutableStateOf(false) }
    var horizontalDragActive by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    val dampedDragAnimation = remember(
        animationScope,
        userInputEnabled,
        toggleOnTap,
        touchSlop,
        dragWidth,
        isLtr
    ) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            userDragEnabled = userInputEnabled,
            onDragStarted = {
                accumulatedDrag = Offset.Zero
                gestureMoved = false
                horizontalDragActive = false
            },
            onDragStopped = {
                if (horizontalDragActive) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    currentOnSelect.value(fraction == 1f)
                    currentOnHorizontalDragActiveChange.value(false)
                } else if (!gestureMoved && toggleOnTap) {
                    fraction = if (currentSelected.value()) 0f else 1f
                    currentOnSelect.value(fraction == 1f)
                }
                accumulatedDrag = Offset.Zero
                gestureMoved = false
                horizontalDragActive = false
            },
            onDrag = { _, dragAmount ->
                accumulatedDrag += dragAmount
                if (!gestureMoved && accumulatedDrag.getDistance() >= touchSlop) {
                    gestureMoved = true
                    if (abs(accumulatedDrag.x) > abs(accumulatedDrag.y)) {
                        horizontalDragActive = true
                        currentOnHorizontalDragActiveChange.value(true)
                    }
                }
                if (horizontalDragActive) {
                    val delta = dragAmount.x / dragWidth
                    fraction =
                        if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                        else (fraction - delta).fastCoerceIn(0f, 1f)
                }
            },
            onDragCancelled = {
                if (horizontalDragActive) {
                    currentOnHorizontalDragActiveChange.value(false)
                }
                fraction = if (currentSelected.value()) 1f else 0f
                animateToValue(fraction)
                accumulatedDrag = Offset.Zero
                gestureMoved = false
                horizontalDragActive = false
            },
            pointerEventPass = PointerEventPass.Initial,
            shouldConsumeDrag = { horizontalDragActive }
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            if (horizontalDragActive) {
                currentOnHorizontalDragActiveChange.value(false)
            }
        }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { fraction ->
                dampedDragAnimation.updateValue(fraction)
            }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentSelected.value() }
            .collectLatest { isSelected ->
                val target = if (isSelected) 1f else 0f
                if (target != fraction) {
                    fraction = target
                }
            }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val accessibilityModifier = if (toggleOnTap) {
        Modifier.semantics {
            role = Role.Switch
            toggleableState = if (currentSelected.value()) {
                ToggleableState.On
            } else {
                ToggleableState.Off
            }
            contentDescription?.let { this.contentDescription = it }
            if (userInputEnabled) {
                onClick {
                    currentOnSelect.value(!currentSelected.value())
                    true
                }
            } else {
                disabled()
            }
        }
    } else {
        Modifier.clearAndSetSemantics {}
    }

    Box(
        modifier
            .heightIn(min = 48.dp)
            .then(accessibilityModifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(ContinuousCapsule)
                .drawBehind {
                    val fraction = dampedDragAnimation.value
                    drawRect(lerp(trackColor, accentColor, fraction))
                }
                .size(64f.dp, 28f.dp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val fraction = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, fraction)
                        else lerp(-padding, -(padding + dragWidth), fraction)
                }
                .then(
                    if (userInputEnabled) {
                        Modifier.then(dampedDragAnimation.modifier)
                    } else {
                        Modifier
                    }
                )
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { ContinuousCapsule },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        if (tokens.quality.supportsBlur) {
                            blur(tokens.control.blurRadius.toPx() * (1f - progress))
                        }
                        if (tokens.quality.supportsRefraction && progress > 0f) {
                            lens(
                                tokens.control.refractionHeight.toPx() * progress,
                                tokens.control.refractionAmount.toPx() * progress
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
