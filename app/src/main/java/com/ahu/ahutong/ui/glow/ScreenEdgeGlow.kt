package com.ahu.ahutong.ui.glow

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Apple Intelligence 风格的屏幕边缘光效（试验版）。
 *
 * 圆角来源优先级：
 * 1. `WindowInsets.getRoundedCorner()` 的逐角半径（API 31+，ColorOS/Pixel 都可用）；
 * 2. `WindowInsets.getDisplayShape()` 的真实路径（API 34+，部分 ROM 只给近似值）；
 * 3. 28dp 兜底。
 *
 * 渲染：RenderEffect 四层描边（宽 6/9/11/15dp，模糊 0/4/12/15dp）。
 * 左上角红色文字是调试读数，用来确认系统上报的圆角与来源。
 */
@Composable
fun ScreenEdgeGlow(modifier: Modifier = Modifier) {
    val view = LocalView.current
    var radii by remember { mutableStateOf<CornerRadii?>(null) }
    var shapePath by remember { mutableStateOf<Path?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(view) {
        repeat(20) {
            val insets = view.rootWindowInsets
            if (insets != null) {
                val read = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    CornerRadii(
                        topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0,
                        topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0,
                        bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
                        bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
                    )
                } else {
                    CornerRadii(0, 0, 0, 0)
                }
                if (read.hasAny) {
                    radii = read
                    return@LaunchedEffect
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val path = runCatching {
                        insets.displayShape?.path?.asComposePath()
                    }.getOrNull()
                    if (path != null) {
                        shapePath = path
                        return@LaunchedEffect
                    }
                }
            }
            delay(100)
        }
    }

    val fittedShape = remember(shapePath, canvasSize) {
        shapePath?.fittedTo(canvasSize.width.toFloat(), canvasSize.height.toFloat())
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

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        GlowLayer(outline = fittedShape, radii = radii, widthDp = 6f, blurDp = 0f, phase = phase)
        GlowLayer(outline = fittedShape, radii = radii, widthDp = 9f, blurDp = 4f, phase = phase)
        GlowLayer(outline = fittedShape, radii = radii, widthDp = 11f, blurDp = 12f, phase = phase)
        GlowLayer(outline = fittedShape, radii = radii, widthDp = 15f, blurDp = 15f, phase = phase)
    }
}

private data class CornerRadii(
    val topLeft: Int,
    val topRight: Int,
    val bottomRight: Int,
    val bottomLeft: Int,
) {
    val hasAny: Boolean
        get() = topLeft > 0 || topRight > 0 || bottomRight > 0 || bottomLeft > 0
}

@Composable
private fun GlowLayer(
    outline: Path?,
    radii: CornerRadii?,
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
        val path = when {
            radii != null -> Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        topLeft = CornerRadius(radii.topLeft.toFloat(), radii.topLeft.toFloat()),
                        topRight = CornerRadius(radii.topRight.toFloat(), radii.topRight.toFloat()),
                        bottomRight = CornerRadius(radii.bottomRight.toFloat(), radii.bottomRight.toFloat()),
                        bottomLeft = CornerRadius(radii.bottomLeft.toFloat(), radii.bottomLeft.toFloat()),
                    )
                )
            }
            outline != null -> outline
            else -> Path().apply {
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

private fun Path.fittedTo(width: Float, height: Float): Path {
    if (width <= 0f || height <= 0f) return this
    val bounds = getBounds()
    if (bounds.width <= 0f || bounds.height <= 0f) return this
    val matrix = Matrix()
    matrix.translate(-bounds.left, -bounds.top)
    matrix.scale(width / bounds.width, height / bounds.height)
    return Path().apply {
        addPath(this@fittedTo)
        transform(matrix)
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
