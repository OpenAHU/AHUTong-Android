package com.ahu.ahutong.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class GuidedTourStep(
    val title: String,
    val body: String,
    val note: String? = null
)

/**
 * 全屏新手导览：暗幕 + 主题色镂空 + 贴着目标的说明卡。
 * 目标边界由 [TourBounds] 提供，滚动或切页后自动跟随。
 */
@Composable
fun GuidedTourOverlay(
    visible: Boolean,
    steps: List<GuidedTourStep>,
    index: Int,
    targetBounds: Rect?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    allowTargetInteraction: Boolean = false,
    allowHighlightedInteraction: Boolean = false,
    nextEnabled: Boolean = true,
    hint: String? = null,
) {
    if (!visible || steps.isEmpty()) return
    val safeIndex = index.coerceIn(0, steps.lastIndex)
    val primary = MaterialTheme.colorScheme.primary

    val animatedBounds = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var hasBounds by remember { mutableStateOf(false) }

    LaunchedEffect(targetBounds) {
        val bounds = targetBounds ?: return@LaunchedEffect
        if (!hasBounds) {
            animatedBounds.snapTo(bounds)
            hasBounds = true
        } else {
            animatedBounds.animateTo(
                targetValue = bounds,
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            )
        }
    }
    val holeRect = if (hasBounds) animatedBounds.value else null

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outer = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }
            if (holeRect == null) {
                drawPath(outer, Color.Black.copy(alpha = 0.35f))
            } else {
                val pad = 6.dp.toPx()
                val padded = Rect(
                    holeRect.left - pad,
                    holeRect.top - pad,
                    holeRect.right + pad,
                    holeRect.bottom + pad
                )
                val hole = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = padded,
                            cornerRadius = CornerRadius(16.dp.toPx())
                        )
                    )
                }
                drawPath(
                    Path.combine(PathOperation.Difference, outer, hole),
                    Color.Black.copy(alpha = 0.35f)
                )
                drawRoundRect(
                    color = primary,
                    topLeft = padded.topLeft,
                    size = Size(padded.width, padded.height),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.5f.dp.toPx())
                )
            }
        }

        // 整页放行 / 只放行镂空区域 / 全部拦截
        when {
            allowTargetInteraction -> Unit
            allowHighlightedInteraction && holeRect != null -> {
                val screenW = with(density) { maxWidth.toPx() }
                val screenH = with(density) { maxHeight.toPx() }
                val padPx = with(density) { 6.dp.toPx() }
                val leftDp = with(density) {
                    (holeRect.left - padPx).coerceAtLeast(0f).toDp()
                }
                val topDp = with(density) {
                    (holeRect.top - padPx).coerceAtLeast(0f).toDp()
                }
                val rightDp = with(density) {
                    (holeRect.right + padPx).coerceAtMost(screenW).toDp()
                }
                val bottomDp = with(density) {
                    (holeRect.bottom + padPx).coerceAtMost(screenH).toDp()
                }
                val holeHeight = (bottomDp - topDp).coerceAtLeast(0.dp)
                TourTouchBlocker(Modifier.fillMaxWidth().height(topDp))
                TourTouchBlocker(
                    Modifier
                        .fillMaxWidth()
                        .height((maxHeight - bottomDp).coerceAtLeast(0.dp))
                        .offset(y = bottomDp)
                )
                TourTouchBlocker(
                    Modifier
                        .width(leftDp)
                        .height(holeHeight)
                        .offset(y = topDp)
                )
                TourTouchBlocker(
                    Modifier
                        .width((maxWidth - rightDp).coerceAtLeast(0.dp))
                        .height(holeHeight)
                        .offset(x = rightDp, y = topDp)
                )
            }
            else -> {
                TourTouchBlocker(Modifier.fillMaxSize())
            }
        }

        var hintSize by remember { mutableStateOf(IntSize.Zero) }
        val hintFloat by rememberInfiniteTransition(label = "tourHintFloat").animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tourHintOffset"
        )
        if (hint != null && holeRect != null) {
            val hintX = with(density) {
                (holeRect.left - 10.dp.toPx() - hintSize.width).coerceAtLeast(8.dp.toPx())
            }
            val hintY = (holeRect.top + holeRect.bottom) / 2f - hintSize.height / 2f
            Text(
                text = hint,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (hintX + hintFloat.dp.toPx()).roundToInt(),
                            hintY.roundToInt()
                        )
                    }
                    .onSizeChanged { hintSize = it }
            )
        }

        var cardHeightPx by remember { mutableStateOf(0) }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val cardWidth = (maxWidth - 32.dp).coerceAtLeast(240.dp)
        val gapPx = with(density) { 22.dp.toPx() }
        // 方向只由该步目标本身决定一次：目标在上半屏 → 卡片放下方，反之放上方。
        // 卡片位置直接跟随已平滑的聚焦框，不另做缓动，避免两者不同步。
        val above = targetBounds != null &&
            (targetBounds.top + targetBounds.bottom) / 2f > screenHeightPx / 2f
        val rawTopPx = when {
            holeRect == null -> (screenHeightPx - cardHeightPx) / 2f
            above -> holeRect.top - gapPx - cardHeightPx
            else -> holeRect.bottom + gapPx
        }
        val minTopPx = with(density) { 88.dp.toPx() }
        val bottomInsetPx = with(density) { 32.dp.toPx() }
        val maxTopPx = (screenHeightPx - cardHeightPx - bottomInsetPx).coerceAtLeast(minTopPx)
        val cardTopPx = rawTopPx.coerceIn(minTopPx, maxTopPx)

        Surface(
            modifier = Modifier
                .offset { IntOffset(16.dp.roundToPx(), cardTopPx.roundToInt()) }
                .width(cardWidth)
                .onSizeChanged { cardHeightPx = it.height },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 16.dp
        ) {
            Crossfade(targetState = safeIndex, animationSpec = tween(180)) { i ->
                val step = steps[i]
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = step.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${i + 1}/${steps.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = step.body,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (step.note != null) {
                        Text(
                            text = step.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onSkip) {
                            Text(
                                "跳过",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (i > 0) {
                            TextButton(onClick = onPrevious) { Text("上一步") }
                        }
                        AppButton(
                            onClick = onNext,
                            enabled = nextEnabled,
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(if (i == steps.lastIndex) "完成" else "下一步")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TourTouchBlocker(modifier: Modifier) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
        }
    )
}
