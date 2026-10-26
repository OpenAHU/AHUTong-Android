package com.ahu.ahutong.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class UpdateStripPhase { AVAILABLE, DOWNLOADING, FAILED, READY }

val UpdateStatusStripHeight = 52.dp

/** Height and radius of the inverted corners that let the page above sit inside the strip. */
val UpdateStatusStripCornerRadius = 16.dp

/** The background extends under system navigation; the top strip alone handles update actions. */
@Composable
fun UpdateStatusStrip(
    phase: UpdateStripPhase,
    progress: Float?,
    mandatory: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = UpdateStatusStripCornerRadius,
) {
    val scheme = MaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    val actualProgress = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    val lightForeground = scheme.onSurface.luminance() > 0.5f
    val availableBase = lerp(scheme.primaryContainer, scheme.primary, 0.08f)
    val green = Color(0xFF10BF4F)
    val onGreen = Color(0xFF102D18)
    val waveGreen = Color(0xFF28C760)
    val downloadBase = lerp(scheme.primaryContainer, Color.Black, if (lightForeground) 0.20f else 0.62f)
    val onDownloadBase = if (lightForeground) scheme.onSurface else scheme.inverseOnSurface
    val background by animateColorAsState(
        targetValue = when (phase) {
            UpdateStripPhase.AVAILABLE -> availableBase
            UpdateStripPhase.FAILED -> scheme.errorContainer
            else -> downloadBase
        },
        animationSpec = tween(260),
        label = "Update strip background",
    )
    val fill = remember {
        Animatable(
            when (phase) {
                UpdateStripPhase.READY -> 1f
                UpdateStripPhase.DOWNLOADING -> actualProgress ?: 0f
                else -> 0f
            }
        )
    }
    val entrance = remember { Animatable(1f) }
    val firstWave = remember { Animatable(1f) }
    val secondWave = remember { Animatable(1f) }
    val unknownWave = remember { Animatable(1f) }

    LaunchedEffect(phase, actualProgress) {
        val target = when (phase) {
            UpdateStripPhase.READY -> 1f
            UpdateStripPhase.DOWNLOADING -> actualProgress ?: 0f
            else -> 0f
        }
        fill.animateTo(target, tween(if (phase == UpdateStripPhase.FAILED) 480 else 350))
    }
    LaunchedEffect(phase) {
        entrance.snapTo(1f)
        if (phase == UpdateStripPhase.AVAILABLE) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(950, easing = LinearEasing))
        }
    }
    LaunchedEffect(phase, mandatory, lifecycleOwner) {
        firstWave.snapTo(1f)
        secondWave.snapTo(1f)
        if (phase == UpdateStripPhase.READY && mandatory) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                firstWave.snapTo(1f)
                secondWave.snapTo(1f)
                while (isActive) {
                    coroutineScope {
                        launch {
                            firstWave.snapTo(0f)
                            firstWave.animateTo(1f, tween(850, easing = LinearEasing))
                        }
                        launch {
                            delay(300)
                            secondWave.snapTo(0f)
                            secondWave.animateTo(1f, tween(850, easing = LinearEasing))
                        }
                        // No frame clock is requested between pairs of finite waves.
                        delay(3_000)
                    }
                }
            }
        }
    }
    LaunchedEffect(phase, actualProgress, lifecycleOwner) {
        if (phase == UpdateStripPhase.DOWNLOADING && actualProgress == null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    unknownWave.snapTo(0f)
                    coroutineScope {
                        launch {
                            unknownWave.animateTo(1f, tween(1_600, easing = LinearEasing))
                        }
                        // Keep a bounded cycle even when system animations are disabled.
                        delay(1_600)
                    }
                }
            }
        }
    }

    val label = phase.stripLabel()
    val clickLabel = when (phase) {
        UpdateStripPhase.AVAILABLE -> "查看更新"
        UpdateStripPhase.DOWNLOADING -> "查看下载进度"
        UpdateStripPhase.FAILED -> "查看更新详情"
        UpdateStripPhase.READY -> "安装更新"
    }
    val accessibleState = when (phase) {
        UpdateStripPhase.AVAILABLE -> "发现新版本"
        UpdateStripPhase.DOWNLOADING -> actualProgress?.let {
            "正在下载，${(it * 100).roundToInt()}%"
        } ?: "正在下载"
        UpdateStripPhase.FAILED -> "下载失败，正在准备重试"
        UpdateStripPhase.READY -> "安装包已就绪"
    } + if (mandatory) "，需要更新" else ""
    val clickAction = onClick

    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val corner = cornerRadius.coerceAtLeast(0.dp)
    val contentModifier = Modifier
            .fillMaxWidth()
            .height(UpdateStatusStripHeight)
            .clipToBounds()
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = clickAction)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = accessibleState
                role = Role.Button
                liveRegion = LiveRegionMode.Polite
                onClick(label = clickLabel) {
                    clickAction()
                    true
                }
                when (phase) {
                    UpdateStripPhase.DOWNLOADING -> {
                        progressBarRangeInfo = actualProgress?.let {
                            ProgressBarRangeInfo(it, 0f..1f)
                        } ?: ProgressBarRangeInfo.Indeterminate
                    }
                    UpdateStripPhase.READY -> progressBarRangeInfo = ProgressBarRangeInfo(1f, 0f..1f)
                    UpdateStripPhase.FAILED -> progressBarRangeInfo = ProgressBarRangeInfo(0f, 0f..1f)
                    UpdateStripPhase.AVAILABLE -> Unit
                }
            }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(UpdateStatusStripHeight + corner + navigationInset)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cornerPx = corner.toPx()
            val tray = Path().apply {
                moveTo(0f, 0f)
                cubicTo(
                    0f, cornerPx * 0.5523f,
                    cornerPx * 0.4477f, cornerPx,
                    cornerPx, cornerPx,
                )
                lineTo(size.width - cornerPx, cornerPx)
                cubicTo(
                    size.width - cornerPx * 0.4477f, cornerPx,
                    size.width, cornerPx * 0.5523f,
                    size.width, 0f,
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            clipPath(tray) {
                drawRect(background)
                if (fill.value > 0f) {
                    drawRect(green, size = Size(size.width * fill.value, size.height))
                }
                if (phase == UpdateStripPhase.DOWNLOADING && actualProgress == null) {
                    stripWave(unknownWave.value, green, strength = 0.3f, fromLeft = true)
                }
                if (phase == UpdateStripPhase.AVAILABLE) {
                    stripWave(entrance.value, scheme.primary, strength = 0.28f)
                }
                if (phase == UpdateStripPhase.READY && mandatory) {
                    stripWave(firstWave.value, waveGreen, strength = 0.7f)
                    stripWave(secondWave.value, waveGreen, strength = 0.7f)
                }
                // Soft shadow from the page layer above, falling onto the strip's top edge.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                        startY = cornerPx,
                        endY = cornerPx + 18.dp.toPx(),
                    ),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = corner)) {
            AnimatedContent(
                targetState = phase,
                modifier = contentModifier,
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    val oldTextExit = if (initialState == UpdateStripPhase.FAILED) {
                        fadeOut(tween(180))
                    } else {
                        fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 }
                    }
                    // Bring the next label in before the departing label loses legibility.
                    fadeIn(tween(120, easing = LinearOutSlowInEasing)) togetherWith oldTextExit
                },
                label = "Update strip text",
            ) { visiblePhase ->
                val textColor = when (visiblePhase) {
                    UpdateStripPhase.FAILED -> scheme.onErrorContainer
                    UpdateStripPhase.READY -> onGreen
                    UpdateStripPhase.DOWNLOADING -> onDownloadBase
                    UpdateStripPhase.AVAILABLE -> scheme.onPrimaryContainer
                }
                Box(Modifier.fillMaxSize()) {
                    UpdateStripLabel(visiblePhase, textColor, scheme.onPrimaryContainer)
                    if (visiblePhase == UpdateStripPhase.DOWNLOADING &&
                        (actualProgress != null || phase == UpdateStripPhase.READY)
                    ) {
                        Box(Modifier.fillMaxSize().drawWithContent {
                            clipRect(right = size.width * fill.value) { this@drawWithContent.drawContent() }
                        }) {
                            UpdateStripLabel(visiblePhase, onGreen, onGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateStripLabel(phase: UpdateStripPhase, color: Color, iconColor: Color) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (phase == UpdateStripPhase.AVAILABLE) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = iconColor)
        }
        Text(
            phase.stripLabel(),
            style = MaterialTheme.typography.labelLarge,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
        )
    }
}

private fun UpdateStripPhase.stripLabel(): String = when (this) {
    UpdateStripPhase.AVAILABLE -> "新版本"
    UpdateStripPhase.DOWNLOADING -> "下载中"
    UpdateStripPhase.FAILED -> "下载失败"
    UpdateStripPhase.READY -> "安装"
}

private fun DrawScope.stripWave(progress: Float, color: Color, strength: Float, fromLeft: Boolean = false) {
    if (progress <= 0f || progress >= 1f) return
    // Like ClassIsland's filled ellipse: the swept area remains filled, then the whole layer fades.
    val waveCenter = if (fromLeft) Offset(0f, size.height * 0.5f) else center
    val coveredRadius = hypot(if (fromLeft) size.width else size.width * 0.5f, size.height * 0.5f)
    val feather = minOf(24.dp.toPx(), coveredRadius * 0.15f)
    val expansion = (progress / 0.68f).coerceAtMost(1f)
    val radius = (coveredRadius + feather) * expansion * expansion
    val fade = ((progress - 0.74f) / 0.26f).coerceIn(0f, 1f)
    val opacity = strength * (progress / 0.05f).coerceAtMost(1f) *
        (1f - FastOutSlowInEasing.transform(fade))
    val softEdge = minOf(feather, radius * 0.35f)
    var previousOpacity = 0f
    // Six nested fills soften only the leading edge; their accumulated interior alpha is opacity.
    repeat(6) { index ->
        val edge = (index + 1f) / 6f
        val targetOpacity = opacity * edge * edge * (3f - 2f * edge)
        val layerOpacity = (targetOpacity - previousOpacity) / (1f - previousOpacity)
        drawCircle(
            color.copy(alpha = layerOpacity),
            radius = radius - softEdge * index / 5f,
            center = waveCenter,
        )
        previousOpacity = targetOpacity
    }
}
