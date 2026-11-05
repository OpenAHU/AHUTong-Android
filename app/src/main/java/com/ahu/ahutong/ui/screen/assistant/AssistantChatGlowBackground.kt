package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 对话页的极低透明度彩色晕动：和输入栏、边缘光共用六色，但只作为背景，
 * 不参与交互，也不影响正文可读性。
 */
@Composable
internal fun AssistantChatGlowBackground(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val phase by rememberInfiniteTransition(label = "chat-glow")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 28_000, easing = LinearEasing)
            ),
            label = "chat-glow-phase"
        )
    val colors = ASSISTANT_GLOW_COLORS
    val alpha = if (dark) 0.16f else 0.10f

    Canvas(modifier = modifier) {
        val t = phase * 2f * PI.toFloat()
        drawRect(
            Brush.linearGradient(
                colors = listOf(
                    colors[0].copy(alpha = alpha * 0.42f),
                    colors[0].copy(alpha = 0f),
                    colors[2].copy(alpha = alpha * 0.36f),
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            )
        )
        glowBlob(
            center = Offset(
                size.width * (0.15f + 0.12f * sin(t)),
                size.height * (0.20f + 0.08f * cos(t)),
            ),
            color = colors[0],
            radius = size.width * 0.78f,
            alpha = alpha,
        )
        glowBlob(
            center = Offset(
                size.width * (0.88f + 0.10f * cos(t + 1.3f)),
                size.height * (0.32f + 0.12f * sin(t + 0.7f)),
            ),
            color = colors[3],
            radius = size.width * 0.66f,
            alpha = alpha * 0.9f,
        )
        glowBlob(
            center = Offset(
                size.width * (0.55f + 0.18f * sin(t + 2.2f)),
                size.height * (0.92f + 0.06f * cos(t + 1.1f)),
            ),
            color = colors[2],
            radius = size.width * 0.82f,
            alpha = alpha * 0.82f,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.glowBlob(
    center: Offset,
    color: Color,
    radius: Float,
    alpha: Float,
) {
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.36f),
                color.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        center = center,
        radius = radius,
    )
}
