package com.ahu.ahutong.ui.glow

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Apple Intelligence 风格的屏幕边缘光效（试验版）。
 *
 * - 轮廓：Android 14+ 用 `Display.getShape()` 的真实屏幕 Path（含厂商圆角），
 *   拿不到时退回 28dp 圆角矩形。
 * - 渲染：RenderEffect 四层描边（宽 6/9/11/15dp，模糊 0/4/12/15dp），
 *   与 SwiftUI 参考实现同一组参数。
 * - 动效：角向渐变 + 6 色停靠点随相位缓慢漂移，单次呼吸约 7 秒。
 *
 * 仅用于效果评估：没有接入任何业务状态。
 */
@Composable
fun ScreenEdgeGlow(modifier: Modifier = Modifier) {
    val view = LocalView.current
    val outline = remember(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { view.display?.shape?.path?.asComposePath() }.getOrNull()
        } else {
            null
        }
    }
    val phase by rememberInfiniteTransition(label = "edge-glow")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7_000, easing = LinearEasing)
            ),
            label = "edge-glow-phase"
        )

    Box(modifier.fillMaxSize()) {
        GlowLayer(outline = outline, widthDp = 6f, blurDp = 0f, phase = phase)
        GlowLayer(outline = outline, widthDp = 9f, blurDp = 4f, phase = phase)
        GlowLayer(outline = outline, widthDp = 11f, blurDp = 12f, phase = phase)
        GlowLayer(outline = outline, widthDp = 15f, blurDp = 15f, phase = phase)
    }
}

@Composable
private fun GlowLayer(
    outline: Path?,
    widthDp: Float,
    blurDp: Float,
    phase: Float,
) {
    val density = LocalDensity.current
    val strokeWidth = with(density) { widthDp.dp.toPx() }
    val blurRadius = with(density) { blurDp.dp.toPx() }
    val fallbackRadius = with(density) { 28.dp.toPx() }
    val stops = remember(phase) { glowStops(phase) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (blurRadius > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Decal)
                }
            }
    ) {
        val path = outline ?: Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(fallbackRadius, fallbackRadius)
                )
            )
        }
        drawPath(
            path = path,
            brush = Brush.sweepGradient(
                colorStops = stops.toTypedArray(),
                center = Offset(size.width / 2f, size.height / 2f)
            ),
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

private val GLOW_COLORS = listOf(
    0xFFBC82F3,
    0xFFF5B9EA,
    0xFF8D9FFF,
    0xFFFF6778,
    0xFFFFBA71,
    0xFFC686FF,
)

/** 6 个颜色停靠点随相位缓慢漂移；与参考实现一样按位置排序后构成角向渐变。 */
private fun glowStops(phase: Float): List<Pair<Float, Color>> {
    val raw = GLOW_COLORS.mapIndexed { index, color ->
        val base = index / GLOW_COLORS.size.toFloat()
        val drift = 0.10f * sin((phase * 2f * PI + index * 1.7).toFloat())
        val position = ((base + drift) % 1f + 1f) % 1f
        position to Color(color)
    }.sortedBy { it.first }

    val start = raw.first()
    val end = raw.last()
    return buildList {
        if (start.first > 0f) add(0f to start.second)
        addAll(raw)
        if (end.first < 1f) add(1f to start.second)
    }
}
