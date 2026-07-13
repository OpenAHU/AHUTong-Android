package com.ahu.ahutong.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.ahu.ahutong.ui.theme.AhuColors

/**
 * Secondary text action (e.g. "管理我的帖子", "删除").
 */
@Composable
fun AhuTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null,
    style: TextStyle = MaterialTheme.typography.labelLarge,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(
            text = text,
            color = color ?: AhuColors.primaryAction,
            style = style,
        )
    }
}
