package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 二级页标题栏右侧的操作按钮。 */
data class TrailingAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit
)

/**
 * 统一的二级页面标题栏。
 * RadiantUI 走新规范样式（固定高度标题栏，右侧最多 3 个统一按钮，可不填满）；
 * Original / Liquid Glass 走保守兼容分支，保持既有标题栏观感。
 */
@Composable
fun SecondaryPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: List<TrailingAction> = emptyList(),
    maxActions: Int = 3,
    subtitle: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val limited = actions.take(maxActions)
    if (isRadiantUi) {
        val headerBg = if (LocalIsLiquidGlassEnabled.current) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surface
        }
        val rowHeight = if (subtitle != null) 64.dp else 60.dp
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to headerBg,
                            0.35f to headerBg,
                            0.68f to headerBg.copy(alpha = 0.85f),
                            1f to headerBg.copy(alpha = 0f)
                        )
                    )
                )
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 22.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
                if (limited.isNotEmpty() || trailingContent != null) {
                    Row(
                        modifier = Modifier.padding(end = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        limited.forEach { action ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = action.onClick) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = action.contentDescription,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        trailingContent?.invoke(this)
                    }
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (limited.isNotEmpty()) {
                Row {
                    limited.forEach { action ->
                        IconButton(onClick = action.onClick) {
                            Icon(action.icon, action.contentDescription)
                        }
                    }
                }
            }
        }
    }
}