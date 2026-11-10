package com.ahu.ahutong.ui.screen.assistant

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.R
import com.ahu.ahutong.assistant.model.AssistantModelLibrary
import com.ahu.ahutong.assistant.model.AssistantModelLibrarySnapshot
import com.ahu.ahutong.assistant.model.AssistantModelProfile
import com.ahu.ahutong.assistant.model.AssistantThinkingLevel
import com.ahu.ahutong.assistant.model.AssistantThinkingPreferences
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 角向渐变按闭合色环采样：0 与 1 取同一个颜色，相位只整体平移色环。
 * 以前每个色标独立漂移、排序后补首尾，漂移交错时会在 3 点钟方向出现断色。
 */
private fun inputGlowStops(phase: Float): List<Pair<Float, Color>> {
    val steps = 48
    return (0..steps).map { index ->
        val position = index / steps.toFloat()
        position to cyclicGlowColor(position + phase)
    }
}

private fun cyclicGlowColor(value: Float): Color {
    val palette = ASSISTANT_GLOW_COLORS
    val wrapped = ((value % 1f) + 1f) % 1f
    val scaled = wrapped * palette.size
    val index = scaled.toInt() % palette.size
    val fraction = scaled - scaled.toInt()
    return lerp(palette[index], palette[(index + 1) % palette.size], fraction)
}

/**
 * AI 输入栏：未聚焦时是胶囊；聚焦后展开为圆角矩形，底部出现模型与思考强度。
 * 两者共用一个上拉面板，强度滑块的档位是 off/low/mid/hi。
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
    val scope = rememberCoroutineScope()

    val library by AssistantModelLibrary.state.collectAsState()
    val active = library.active
    var level by remember { mutableStateOf(AssistantThinkingPreferences.level()) }
    var pickerOpen by remember { mutableStateOf(false) }
    var keepExpanded by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val expanded = focused || pickerOpen || keepExpanded

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
    val glowAlpha = if (expanded) 1f else 0.55f
    val blurPx = with(density) { 6.dp.toPx() }
    val shape = RoundedCornerShape(28.dp)
    val rowBottomPadding by animateDpAsState(if (expanded) 0.dp else 6.dp, label = "input-row-bottom")
    val barAlpha by animateFloatAsState(if (expanded) 1f else 0f, label = "input-bar-alpha")

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
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = rowBottomPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
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
                if (!expanded) {
                    AssistantSendButton(
                        canSend = canSend,
                        colors = colors,
                        disabledBg = disabledBg,
                        disabledIcon = disabledIcon,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSend()
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(120)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 0.dp)
                        .alpha(barAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PickerChip(
                        text = (active?.displayName?.takeIf { it.isNotBlank() } ?: "未配置模型") +
                            " · " + level.label,
                        onClick = { pickerOpen = true },
                    )
                    Spacer(Modifier.weight(1f))
                    AssistantSendButton(
                        canSend = canSend,
                        colors = colors,
                        disabledBg = disabledBg,
                        disabledIcon = disabledIcon,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSend()
                        },
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        AssistantModelPickerSheet(
            snapshot = library,
            level = level,
            onDismiss = { pickerOpen = false },
            onLevelChange = { next ->
                level = next
                AssistantThinkingPreferences.setLevel(next)
            },
            onSelectModel = { profile ->
                AssistantModelLibrary.setActive(profile.id)
                pickerOpen = false
                keepExpanded = true
                scope.launch {
                    kotlinx.coroutines.delay(220)
                    runCatching { focusRequester.requestFocus() }
                    kotlinx.coroutines.delay(420)
                    keepExpanded = false
                }
            },
        )
    }
}

@Composable
private fun AssistantSendButton(
    canSend: Boolean,
    colors: androidx.compose.material3.ColorScheme,
    disabledBg: Color,
    disabledIcon: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (canSend) colors.primary else disabledBg)
            .clickable(enabled = canSend, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ArrowUpward,
            contentDescription = "发送",
            tint = if (canSend) colors.onPrimary else disabledIcon,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PickerChip(
    text: String,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .alpha(if (dimmed) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_assistant_sparkle_mini),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

private data class ModelLogo(@DrawableRes val res: Int, val monochrome: Boolean)

private fun modelLogo(profile: AssistantModelProfile): ModelLogo? {
    val key = "${profile.providerLabel} ${profile.model} ${profile.baseUrl}".lowercase()
    return when {
        "deepseek" in key -> ModelLogo(R.drawable.logo_deepseek, false)
        "openai" in key || "gpt" in key || Regex("""\bo[1-9]""").containsMatchIn(key) ->
            ModelLogo(R.drawable.logo_openai, true)
        "kimi" in key || "moonshot" in key -> ModelLogo(R.drawable.logo_moonshot, true)
        "qwen" in key || "通义" in key || "dashscope" in key ->
            ModelLogo(R.drawable.logo_qwen, false)
        "claude" in key || "anthropic" in key -> ModelLogo(R.drawable.logo_anthropic, true)
        "minimax" in key -> ModelLogo(R.drawable.logo_minimax, false)
        "doubao" in key || "豆包" in key || "bytedance" in key ||
            "volces" in key || "ark" in key -> ModelLogo(R.drawable.logo_bytedance, false)
        "glm" in key || "智谱" in key || "z.ai" in key || "zai" in key || "bigmodel" in key ->
            ModelLogo(R.drawable.logo_zai, true)
        else -> null
    }
}

@Composable
private fun AssistantModelPickerSheet(
    snapshot: AssistantModelLibrarySnapshot,
    level: AssistantThinkingLevel,
    onDismiss: () -> Unit,
    onLevelChange: (AssistantThinkingLevel) -> Unit,
    onSelectModel: (AssistantModelProfile) -> Unit,
) {
    val active = snapshot.active
    AppModalBottomSheet(title = "模型与思考", onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("思考强度", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    text = level.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ThinkingLevelSlider(
                level = level,
                enabled = active?.supportsThinking == true,
                onChange = onLevelChange,
            )
            if (active != null && !active.supportsThinking) {
                Text(
                    text = "当前模型标记为不支持思考，强度设置不会发给服务端。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Text("模型", style = MaterialTheme.typography.titleSmall)
            if (snapshot.profiles.isEmpty()) {
                Text(
                    text = "还没有配置模型，请到 AI 设置里添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                snapshot.profiles.forEach { profile ->
                    val selected = profile.id == active?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectModel(profile) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val logo = modelLogo(profile)
                        if (logo == null) {
                            Icon(
                                painter = painterResource(R.drawable.ic_assistant_sparkle_fill),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        } else if (logo.monochrome) {
                            Icon(
                                painter = painterResource(logo.res),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Image(
                                painter = painterResource(logo.res),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildList {
                                    add(profile.providerLabel.ifBlank { "自定义" })
                                    if (profile.supportsVision) add("视觉")
                                    if (profile.supportsThinking) add("思考")
                                }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "当前模型",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 四档离散渐变滑块：圆钮跟手连续移动，松手落到最近的档位。 */
@Composable
private fun ThinkingLevelSlider(
    level: AssistantThinkingLevel,
    enabled: Boolean,
    onChange: (AssistantThinkingLevel) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val knobRadiusPx = with(density) { 14.dp.toPx() }
    var widthPx by remember { mutableStateOf(0) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentLevel = rememberUpdatedState(level)
    val animatedFraction by animateFloatAsState(
        targetValue = level.ordinal / 3f,
        animationSpec = tween(160),
        label = "thinking-knob",
    )
    val fraction = dragFraction ?: animatedFraction
    // 三段高饱和渐变，比原来的两条相近紫色更跳。
    val trackColors = listOf(
        Color(0xFFD05CFF),
        Color(0xFF8B6BFF),
        Color(0xFF4F8CFF),
    )
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest

    fun fractionAt(x: Float): Float {
        if (widthPx <= knobRadiusPx * 2f) return 0f
        return ((x - knobRadiusPx) / (widthPx - knobRadiusPx * 2f)).coerceIn(0f, 1f)
    }

    fun pick(value: Float) {
        val index = (value * 3f).roundToInt().coerceIn(0, 3)
        val next = AssistantThinkingLevel.entries[index]
        if (next != currentLevel.value) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(next)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(enabled, widthPx) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = fractionAt(down.position.x)
                    dragFraction = start
                    pick(start)
                    try {
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val value = fractionAt(change.position.x)
                            dragFraction = value
                            pick(value)
                            change.consume()
                            pressed = change.pressed
                        }
                    } finally {
                        dragFraction = null
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val trackHeight = 24.dp.toPx()
            val trackRadius = trackHeight / 2f
            // 球比槽高；球心只走到槽两端圆角的圆心，球体外缘刚好贴住容器。
            val trackLeft = knobRadiusPx - trackRadius
            val trackRight = size.width - knobRadiusPx + trackRadius
            val leftCenter = trackLeft + trackRadius
            val rightCenter = trackRight - trackRadius
            val centerY = size.height / 2f
            val knobX = leftCenter + (rightCenter - leftCenter) * fraction

            drawRoundRect(
                color = inactive,
                topLeft = Offset(trackLeft, centerY - trackHeight / 2),
                size = Size(trackRight - trackLeft, trackHeight),
                cornerRadius = CornerRadius(trackRadius, trackRadius),
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = trackColors,
                    startX = trackLeft,
                    endX = trackRight,
                ),
                topLeft = Offset(trackLeft, centerY - trackHeight / 2),
                size = Size((knobX - trackLeft).coerceAtLeast(0f), trackHeight),
                cornerRadius = CornerRadius(trackRadius, trackRadius),
            )
            repeat(4) { index ->
                val x = leftCenter + (rightCenter - leftCenter) * (index / 3f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = 1.4.dp.toPx(),
                    center = Offset(x, centerY),
                )
            }
            // 球在槽里的投影：先画一层偏移阴影，再画白色球体。
            drawCircle(
                color = Color.Black.copy(alpha = 0.12f),
                radius = knobRadiusPx + 1.5.dp.toPx(),
                center = Offset(knobX, centerY + 2.dp.toPx()),
            )
            drawCircle(
                color = Color.White,
                radius = knobRadiusPx,
                center = Offset(knobX, centerY),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.08f),
                radius = knobRadiusPx,
                center = Offset(knobX, centerY + 1.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
