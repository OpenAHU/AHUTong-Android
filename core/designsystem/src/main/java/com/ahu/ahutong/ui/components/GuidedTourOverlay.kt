package com.ahu.ahutong.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GuidedTourStep(val title: String, val body: String)

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
) {
    if (!visible || steps.isEmpty()) return
    val safeIndex = index.coerceIn(0, steps.lastIndex)
    val primary = MaterialTheme.colorScheme.primary

    val left = remember { Animatable(0f) }
    val top = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    val bottom = remember { Animatable(0f) }
    var hasBounds by remember { mutableStateOf(false) }

    LaunchedEffect(targetBounds) {
        val bounds = targetBounds ?: return@LaunchedEffect
        if (!hasBounds) {
            left.snapTo(bounds.left)
            top.snapTo(bounds.top)
            right.snapTo(bounds.right)
            bottom.snapTo(bounds.bottom)
            hasBounds = true
        } else {
            left.animateTo(bounds.left, tween(300))
            top.animateTo(bounds.top, tween(300))
            right.animateTo(bounds.right, tween(300))
            bottom.animateTo(bounds.bottom, tween(300))
        }
    }
    val holeRect = if (hasBounds) {
        Rect(left.value, top.value, right.value, bottom.value)
    } else {
        null
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outer = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }
            if (holeRect == null) {
                drawPath(outer, Color.Black.copy(alpha = 0.78f))
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
                            cornerRadius = CornerRadius(20.dp.toPx())
                        )
                    )
                }
                drawPath(
                    Path.combine(PathOperation.Difference, outer, hole),
                    Color.Black.copy(alpha = 0.78f)
                )
                drawRoundRect(
                    color = primary.copy(alpha = 0.22f),
                    topLeft = padded.topLeft,
                    size = Size(padded.width, padded.height),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 8.dp.toPx())
                )
                drawRoundRect(
                    color = primary,
                    topLeft = padded.topLeft,
                    size = Size(padded.width, padded.height),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // 暗幕吞掉点击，避免引导期间误触底层页面
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } }
        )

        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 4.dp)
        ) {
            Text("跳过", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        var cardHeightPx by remember { mutableStateOf(0) }
        val cardWidth = (maxWidth - 32.dp).coerceAtLeast(240.dp)
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val cardTop = when {
            holeRect == null -> (maxHeight - with(density) { cardHeightPx.toDp() }) / 2
            (holeRect.top + holeRect.bottom) / 2f > screenHeightPx / 2f ->
                with(density) { (holeRect.top - 22.dp.toPx() - cardHeightPx).toDp() }
            else -> with(density) { (holeRect.bottom + 22.dp.toPx()).toDp() }
        }
        val maxTop = (maxHeight - with(density) { cardHeightPx.toDp() } - 32.dp)
            .coerceAtLeast(88.dp)
        val clampedTop = cardTop.coerceIn(88.dp, maxTop)

        Surface(
            modifier = Modifier
                .offset(x = 16.dp, y = clampedTop)
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
                    Row(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (i > 0) {
                            TextButton(onClick = onPrevious) { Text("上一步") }
                        }
                        AppButton(
                            onClick = onNext,
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
