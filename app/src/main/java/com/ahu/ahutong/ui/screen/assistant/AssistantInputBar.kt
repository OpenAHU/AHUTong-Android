package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val INPUT_GLOW_COLORS = listOf(
    0xFFBC82F3,
    0xFFF5B9EA,
    0xFF8D9FFF,
    0xFFFF6778,
    0xFFFFBA71,
    0xFFC686FF,
)

/** 与边缘光同一套颜色的角向渐变；停靠点随相位缓慢漂移。 */
private fun inputGlowStops(phase: Float): List<Pair<Float, Color>> {
    val raw = INPUT_GLOW_COLORS.mapIndexed { index, color ->
        val base = index / INPUT_GLOW_COLORS.size.toFloat()
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

/**
 * AI 输入栏：胶囊输入框 + 圆形发送键。
 *
 * 颜色随深浅色主题变化；描边与发送键共用边缘光那套 6 色渐变，
 * 输入框获得焦点时渐变更亮。不依赖任何组件包。
 */
@Composable
fun AssistantInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "问点什么…"
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val canSend = enabled && value.isNotBlank()
    val dark = colors.background.luminance() < 0.5f

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // 只在绘制阶段读取相位，避免整条输入栏每帧重组。
    val phase = rememberInfiniteTransition(label = "input-glow")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9_000, easing = LinearEasing)
            ),
            label = "input-glow-phase"
        )

    val container = if (dark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (dark) Color.White else Color(0xFF111111)
    val hintColor = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.42f)
    val disabledBg = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.06f)
    val disabledIcon = if (dark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f)
    val glowAlpha = if (focused) 1f else 0.55f
    val blurPx = with(density) { 6.dp.toPx() }
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
    ) {
        // 外层柔光：同一渐变描边 + 模糊，和屏幕边缘光同源。
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    renderEffect = BlurEffect(blurPx, blurPx, TileMode.Decal)
                }
        ) {
            val stroke = with(density) { 7.dp.toPx() }
            val inset = stroke / 2f
            val radius = (with(density) { 28.dp.toPx() } - inset).coerceAtLeast(0f)
            drawRoundRect(
                brush = Brush.sweepGradient(
                    colorStops = inputGlowStops(phase.value).toTypedArray(),
                    center = Offset(size.width / 2f, size.height / 2f)
                ),
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = stroke),
                alpha = 0.32f * glowAlpha
            )
        }
        // 内层细描边。
        Canvas(Modifier.matchParentSize()) {
            val stroke = with(density) { 2.dp.toPx() }
            val inset = stroke / 2f
            val radius = (with(density) { 28.dp.toPx() } - inset).coerceAtLeast(0f)
            drawRoundRect(
                brush = Brush.sweepGradient(
                    colorStops = inputGlowStops(phase.value).toTypedArray(),
                    center = Offset(size.width / 2f, size.height / 2f)
                ),
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = stroke),
                alpha = 0.75f * glowAlpha
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                enabled = enabled,
                interactionSource = interactionSource,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                cursorBrush = SolidColor(colors.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSend) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSend()
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = hintColor
                        )
                    }
                    innerTextField()
                }
            )
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (canSend) colors.primary else disabledBg)
                        .clickable(enabled = canSend) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSend()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "发送",
                        tint = if (canSend) colors.onPrimary else disabledIcon,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
