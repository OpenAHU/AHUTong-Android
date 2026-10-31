package com.ahu.ahutong.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.ahu.ahutong.ui.theme.LiquidGlassQuality
import com.ahu.ahutong.ui.theme.LocalLiquidGlassTokens
import com.ahu.ahutong.ui.utils.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousCapsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) {
    val tokens = LocalLiquidGlassTokens.current
    val surfaceStyle = tokens.control
    val animationScope = rememberCoroutineScope()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    val fallbackSurface = when {
        surfaceColor.isSpecified -> surfaceColor
        tint.isSpecified -> tint.copy(alpha = 0.24f)
            .compositeOver(MaterialTheme.colorScheme.secondaryContainer)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val effectiveInteractive = isInteractive && enabled
    val visualModifier = when (tokens.quality) {
        LiquidGlassQuality.Disabled -> Modifier
            .clip(ContinuousCapsule)
            .background(fallbackSurface)

        LiquidGlassQuality.Tinted -> Modifier
            .clip(ContinuousCapsule)
            .background(
                when {
                    surfaceColor.isSpecified -> surfaceColor
                    tint.isSpecified -> tint.copy(alpha = 0.24f)
                        .compositeOver(surfaceStyle.legacyTint)
                    else -> surfaceStyle.legacyTint
                }
            )
            .border(0.5.dp, surfaceStyle.outline, ContinuousCapsule)

        LiquidGlassQuality.Blurred,
        LiquidGlassQuality.Refractive -> Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { ContinuousCapsule },
            effects = {
                vibrancy()
                blur(surfaceStyle.blurRadius.toPx())
                if (tokens.quality.supportsRefraction) {
                    lens(
                        surfaceStyle.refractionHeight.toPx(),
                        surfaceStyle.refractionAmount.toPx()
                    )
                }
            },
            layerBlock = if (effectiveInteractive) {
                {
                    val width = size.width
                    val height = size.height

                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val offset = interactiveHighlight.offset
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                    val maxDragScale = 4f.dp.toPx() / size.height
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX =
                        scale +
                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                            (width / height).fastCoerceAtMost(1f)
                    scaleY =
                        scale +
                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                            (height / width).fastCoerceAtMost(1f)
                }
            } else {
                null
            },
            onDrawSurface = {
                drawRect(surfaceStyle.tint)
                if (tint.isSpecified) {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.75f))
                }
                if (surfaceColor.isSpecified) {
                    drawRect(surfaceColor)
                }
            }
        )
    }

    Row(
        modifier
            .alpha(if (enabled) 1f else 0.48f)
            .then(visualModifier)
            .clickable(
                interactionSource = null,
                indication = if (effectiveInteractive) null else LocalIndication.current,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick
            )
            .then(
                if (effectiveInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .height(48f.dp)
            .padding(horizontal = 16f.dp),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
