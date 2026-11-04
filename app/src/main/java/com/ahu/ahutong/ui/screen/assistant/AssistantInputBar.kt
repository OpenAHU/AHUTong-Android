package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * AI 输入栏：胶囊输入框 + 圆形发送键。
 *
 * 不依赖任何组件包；颜色全部取自 MaterialTheme 语义色，随深浅色与用户主题自动变化。
 * 发送键使用主题色 primary。
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
    val canSend = enabled && value.isNotBlank()
    val dark = colors.background.luminance() < 0.5f
    // 亮色下用近白、暗色下用深灰；不取主题容器色，避免被染成粉/绿。
    val container = if (dark) Color(0xFF1C1C1E).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.94f)
    val borderColor = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    val textColor = if (dark) Color.White else Color(0xFF111111)
    val hintColor = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.42f)
    val disabledBg = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.06f)
    val disabledIcon = if (dark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f)
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            enabled = enabled,
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
