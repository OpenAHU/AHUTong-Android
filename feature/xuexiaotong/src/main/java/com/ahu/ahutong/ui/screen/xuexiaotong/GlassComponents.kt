package com.ahu.ahutong.ui.screen.xuexiaotong

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

fun Modifier.gaussianShadow(
    blur: Dp = 22.dp,
    alpha: Float = 0.18f,
    offsetY: Dp = 5.dp
): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val b = blur.toPx()
        val oy = offsetY.toPx()
        val corner = 24.dp.toPx()
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            this.alpha = (255 * alpha).toInt()
            maskFilter = BlurMaskFilter(b, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawRoundRect(
            -b, -b + oy, size.width + b, size.height + b,
            corner, corner, paint
        )
    }
}

fun Modifier.glassPill(
    backdrop: Backdrop?,
    blurRadius: Dp = 12.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 16.dp,
    withLens: Boolean = true,
    tintColor: Color = Color.White,
    tintAlpha: Float = 0.08f
): Modifier {
    if (backdrop == null) {
        return this.background(tintColor.copy(alpha = 0.2f), RoundedCornerShape(50))
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedCornerShape(50) },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            if (withLens) {
                lens(
                    refractionHeight = refractionHeight.toPx(),
                    refractionAmount = refractionAmount.toPx(),
                    depthEffect = true
                )
            }
        },
        highlight = { Highlight.Default },
        onDrawSurface = {
            drawRect(tintColor.copy(alpha = tintAlpha))
        }
    )
}

@Composable
fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}