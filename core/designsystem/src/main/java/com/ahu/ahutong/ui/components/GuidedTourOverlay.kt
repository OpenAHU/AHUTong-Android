package com.ahu.ahutong.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

/** 纸花参数：参考 canvas-confetti 的物理模型，绘制时按屏幕尺寸展开。 */
private data class ConfettiPiece(
    val fromLeft: Boolean,
    val angle: Float,
    val speed: Float,
    val originY: Float,
    val spin: Float,
    val flipFrequency: Float,
    val flipPhase: Float,
    val widthDp: Float,
    val heightDp: Float,
    val colorIndex: Int,
    val delaySeconds: Float,
    val round: Boolean
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
    allowHighlightedInteraction: Boolean = false,
    nextEnabled: Boolean = true,
    hint: String? = null,
    hintBelow: Boolean = false,
    celebrate: Boolean = false,
) {
    if (steps.isEmpty()) return
    val transition = remember {
        MutableTransitionState(false).apply { targetState = visible }
    }
    LaunchedEffect(visible) { transition.targetState = visible }
    AnimatedVisibility(
        visibleState = transition,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        GuidedTourOverlayBody(
            steps = steps,
            index = index,
            targetBounds = targetBounds,
            onNext = onNext,
            onPrevious = onPrevious,
            onSkip = onSkip,
            allowHighlightedInteraction = allowHighlightedInteraction,
            nextEnabled = nextEnabled,
            hint = hint,
            hintBelow = hintBelow,
            celebrate = celebrate
        )
    }
}

@Composable
private fun GuidedTourOverlayBody(
    steps: List<GuidedTourStep>,
    index: Int,
    targetBounds: Rect?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    allowHighlightedInteraction: Boolean,
    nextEnabled: Boolean,
    hint: String?,
    hintBelow: Boolean,
    celebrate: Boolean
) {
    val safeIndex = index.coerceIn(0, steps.lastIndex)
    val primary = MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val targetAbove = targetBounds != null &&
            (targetBounds.top + targetBounds.bottom) / 2f > screenHeightPx / 2f
        val targetSide = if (targetAbove) 1f else 0f

        val progress = remember { Animatable(1f) }
        var startRect by remember { mutableStateOf(Rect.Zero) }
        var endRect by remember { mutableStateOf(Rect.Zero) }
        var startSide by remember { mutableFloatStateOf(targetSide) }
        var endSide by remember { mutableFloatStateOf(targetSide) }
        var hasBounds by remember { mutableStateOf(false) }
        var animatingStep by remember { mutableStateOf(false) }

        // 切步：从当前姿态向新目标做一次形变
        LaunchedEffect(safeIndex) {
            if (!hasBounds) return@LaunchedEffect
            animatingStep = true
            startRect = lerpRect(startRect, endRect, progress.value)
            startSide += (endSide - startSide) * progress.value
            progress.snapTo(0f)
            progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            animatingStep = false
        }

        // 边界变化：切步动画中只更新终点；动画结束后直接跟随（卡片展开等逐帧变化）
        LaunchedEffect(targetBounds, targetSide) {
            val target = targetBounds ?: return@LaunchedEffect
            if (!hasBounds) {
                startRect = target
                endRect = target
                startSide = targetSide
                endSide = targetSide
                progress.snapTo(1f)
                hasBounds = true
                return@LaunchedEffect
            }
            if (animatingStep || progress.value < 1f) {
                endRect = target
                endSide = targetSide
            } else {
                startRect = target
                endRect = target
                startSide = targetSide
                endSide = targetSide
                progress.snapTo(1f)
            }
        }

        val t = if (hasBounds) progress.value else 0f
        val holeRect = if (hasBounds) lerpRect(startRect, endRect, t) else null
        val sideBlend = if (hasBounds) startSide + (endSide - startSide) * t else 0f

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

        // 只放行镂空区域 / 全部拦截
        when {
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

        // 收尾卡片：两侧各喷一次纸花（物理参考 canvas-confetti），落完即止
        if (celebrate) {
            val burst = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                burst.snapTo(0f)
                burst.animateTo(1f, tween(durationMillis = 3_400, easing = LinearEasing))
            }
            val random = remember { kotlin.random.Random(20261120) }
            val pieces = remember {
                List(160) { index ->
                    val fromLeft = index % 2 == 0
                    val baseAngle = if (fromLeft) {
                        -Math.PI.toFloat() / 2f + 0.42f
                    } else {
                        -Math.PI.toFloat() / 2f - 0.42f
                    }
                    ConfettiPiece(
                        fromLeft = fromLeft,
                        angle = baseAngle + (random.nextFloat() - 0.5f) * 0.84f,
                        speed = 0.62f + random.nextFloat() * 0.45f,
                        originY = 0.55f + random.nextFloat() * 0.15f,
                        spin = (random.nextFloat() * 2f - 1f) * 720f,
                        flipFrequency = 5f + random.nextFloat() * 7f,
                        flipPhase = random.nextFloat() * 6.28f,
                        widthDp = 5f + random.nextFloat() * 5f,
                        heightDp = 9f + random.nextFloat() * 9f,
                        colorIndex = random.nextInt(6),
                        delaySeconds = random.nextFloat() * 0.16f,
                        round = random.nextFloat() < 0.2f
                    )
                }
            }
            val progress = burst.value
            if (progress > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    val palette = listOf(
                        Color(0xFFEF476F),
                        Color(0xFFFFD166),
                        Color(0xFF06D6A0),
                        Color(0xFF118AB2),
                        Color(0xFFB388EB),
                        Color(0xFFFF8FAB)
                    )
                    val elapsed = progress * 3.4f
                    val drag = 2.8f
                    val gravity = size.height * 1.05f
                    pieces.forEach { piece ->
                        val t = elapsed - piece.delaySeconds
                        if (t <= 0f) return@forEach
                        val x0 = if (piece.fromLeft) -4.dp.toPx() else size.width + 4.dp.toPx()
                        val y0 = size.height * piece.originY
                        val speedPx = size.height * piece.speed
                        val vx0 = kotlin.math.cos(piece.angle) * speedPx
                        val vy0 = kotlin.math.sin(piece.angle) * speedPx
                        val decay = kotlin.math.exp(-drag * t)
                        val travel = (1f - decay) / drag
                        val x = x0 + vx0 * travel
                        val y = y0 + (vy0 + gravity / drag) * travel - (gravity / drag) * t
                        if (y > size.height * 1.15f || x < -size.width * 0.1f || x > size.width * 1.1f) {
                            return@forEach
                        }
                        val alpha = ((1f - progress) / 0.22f).coerceIn(0f, 1f)
                        if (alpha <= 0f) return@forEach
                        val w = piece.widthDp.dp.toPx()
                        val h = piece.heightDp.dp.toPx()
                        rotate(degrees = piece.spin * t, pivot = Offset(x, y)) {
                            val flip = 0.25f + 0.75f * kotlin.math.abs(
                                kotlin.math.cos(piece.flipFrequency * t + piece.flipPhase)
                            )
                            scale(scaleX = flip, scaleY = 1f, pivot = Offset(x, y)) {
                                if (piece.round) {
                                    drawCircle(
                                        color = palette[piece.colorIndex].copy(alpha = alpha),
                                        radius = w * 0.6f,
                                        center = Offset(x, y)
                                    )
                                } else {
                                    drawRoundRect(
                                        color = palette[piece.colorIndex].copy(alpha = alpha),
                                        topLeft = Offset(x - w / 2f, y - h / 2f),
                                        size = Size(w, h),
                                        cornerRadius = CornerRadius(1.5.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
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
            val hintX: Float
            val hintY: Float
            if (hintBelow) {
                hintX = (holeRect.left + holeRect.right) / 2f - hintSize.width / 2f
                hintY = with(density) { holeRect.bottom + 8.dp.toPx() }
            } else {
                hintX = with(density) {
                    (holeRect.left - 10.dp.toPx() - hintSize.width).coerceAtLeast(8.dp.toPx())
                }
                hintY = (holeRect.top + holeRect.bottom) / 2f - hintSize.height / 2f
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .offset {
                        val floatPx = hintFloat.dp.toPx()
                        IntOffset(
                            (hintX + if (hintBelow) 0f else floatPx).roundToInt(),
                            (hintY + if (hintBelow) floatPx else 0f).roundToInt()
                        )
                    }
                    .onSizeChanged { hintSize = it }
            )
        }

        var cardHeightPx by remember { mutableStateOf(0) }
        val cardWidth = (maxWidth - 32.dp).coerceAtLeast(240.dp)
        // 框下方有提示时给提示和浮动留出空间
        val cardGapDp = if (hint != null && hintBelow) 48.dp else 22.dp
        val gapPx = with(density) { cardGapDp.toPx() }
        // 框与卡片共用同一个进度：卡片在“下方锚点”和“上方锚点”之间随方向混合，
        // 切步时从当前姿态连续过渡到新姿态，不会在动画开始前瞬间换边。
        val rawTopPx = when {
            holeRect == null -> (screenHeightPx - cardHeightPx) / 2f
            else -> {
                val belowTop = holeRect.bottom + gapPx
                val aboveTop = holeRect.top - gapPx - cardHeightPx
                belowTop + (aboveTop - belowTop) * sideBlend
            }
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
            AnimatedContent(
                targetState = safeIndex,
                transitionSpec = {
                    (fadeIn(tween(180)) togetherWith fadeOut(tween(120)))
                        .using(SizeTransform(clip = true))
                },
                label = "tourCard"
            ) { i ->
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
                        text = emphasizeTourBody(
                            step.body,
                            MaterialTheme.colorScheme.primary
                        ),
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

private fun lerpRect(start: Rect, end: Rect, fraction: Float): Rect = Rect(
    left = start.left + (end.left - start.left) * fraction,
    top = start.top + (end.top - start.top) * fraction,
    right = start.right + (end.right - start.right) * fraction,
    bottom = start.bottom + (end.bottom - start.bottom) * fraction
)

/** 由主窗口注入、在作业详情弹窗窗口内复用的导览层。 */
data class GuidedTourDialogOverlayState(
    val steps: List<GuidedTourStep>,
    val index: Int,
    val targetBounds: Rect?,
    val hint: String?,
    val hintBelow: Boolean,
    val nextEnabled: Boolean,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSkip: () -> Unit
)

val LocalGuidedTourDialogOverlay =
    androidx.compose.runtime.staticCompositionLocalOf<GuidedTourDialogOverlayState?> { null }

/** 导览文案里的 **重点** 标记：加粗 + 主题色，其余保持正文样式。 */
private fun emphasizeTourBody(text: String, accent: androidx.compose.ui.graphics.Color) =
    buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val start = text.indexOf("**", cursor)
            if (start < 0) {
                append(text.substring(cursor))
                break
            }
            append(text.substring(cursor, start))
            val end = text.indexOf("**", start + 2)
            if (end < 0) {
                append(text.substring(start))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) {
                append(text.substring(start + 2, end))
            }
            cursor = end + 2
        }
    }
