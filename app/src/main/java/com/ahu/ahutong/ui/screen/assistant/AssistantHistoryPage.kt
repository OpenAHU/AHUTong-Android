package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AssistantHeaderIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 历史对话页：与 NEXT 一致，按会话展示最近一条预览、消息数、时间，
 * 可打开、删除或直接新建对话。
 */
@Composable
internal fun AssistantHistoryPage(
    sessions: List<AssistantSessionSummary>,
    currentSessionId: String?,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleting by remember { mutableStateOf<AssistantSessionSummary?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            AssistantChatGlowBackground(Modifier.matchParentSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistantHeaderIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        description = "返回",
                        onClick = onBack,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "历史对话",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    AssistantHeaderIconButton(
                        icon = Icons.Rounded.Add,
                        description = "新建对话",
                        onClick = onNewChat,
                    )
                }
    
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (sessions.isEmpty()) {
                        item(key = "empty") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        "还没有历史对话。",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                    items(sessions, key = { it.id }) { session ->
                        HistorySessionCard(
                            session = session,
                            current = session.id == currentSessionId,
                            onOpen = { onOpen(session.id) },
                            onDelete = { deleting = session },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这段对话？") },
            text = {
                Text("「${session.title}」会从本机移除，无法恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(session.id)
                        deleting = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HistorySessionCard(
    session: AssistantSessionSummary,
    current: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainerLow,
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "当前",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                        )
                    }
                }
                if (session.preview.isNotBlank()) {
                    Text(
                        session.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${session.messageCount} 条消息 · ${formatSessionTime(session.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpen) { Text("打开") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

private fun formatSessionTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "未知时间"
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(epochMillis))
    }.getOrDefault("未知时间")
}
