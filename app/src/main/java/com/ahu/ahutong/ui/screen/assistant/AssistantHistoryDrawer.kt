package com.ahu.ahutong.ui.screen.assistant

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.R
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppTextField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/**
 * 对话历史抽屉：只列标题，当前会话高亮；长按可置顶、重命名或删除。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AssistantHistoryDrawer(
    sessions: List<AssistantSessionSummary>,
    currentSessionId: String?,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRename: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<AssistantSessionSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<AssistantSessionSummary?>(null) }

    val filtered = remember(sessions, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            sessions
        } else {
            sessions.filter {
                it.title.contains(needle, true) || it.searchText.contains(needle, true)
            }
        }
    }
    val pinned = filtered.filter { it.pinned }
    val unpinned = filtered.filterNot { it.pinned }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    fun dayOf(session: AssistantSessionSummary): LocalDate =
        Instant.ofEpochMilli(session.updatedAt).atZone(zone).toLocalDate()
    val groups = listOf(
        "今天" to unpinned.filter { dayOf(it) == today },
        "昨天" to unpinned.filter { dayOf(it) == today.minusDays(1) },
        "更早" to unpinned.filter { dayOf(it).isBefore(today.minusDays(1)) }
    ).filter { it.second.isNotEmpty() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .width(maxWidth)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 10.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "对话",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    AssistantHeaderIconButton(
                        icon = R.drawable.ic_assistant_add,
                        description = "新对话",
                        onClick = onNewChat,
                    )
                    AssistantHeaderIconButton(
                        icon = com.ahu.ahutong.core.designsystem.R.drawable.ic_config,
                        description = "AI 设置",
                        onClick = onOpenSettings,
                    )
                }
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (pinned.isNotEmpty()) {
                        item(key = "header_pinned") { SectionHeader("置顶") }
                        items(pinned, key = { "pin_" + it.id }) { session ->
                            SessionRow(
                                session = session,
                                selected = session.id == currentSessionId,
                                onOpen = { onOpen(session.id) },
                                onTogglePin = { onTogglePin(session.id) },
                                onRename = { renameTarget = session },
                                onDelete = { deleteTarget = session },
                            )
                        }
                    }
                    groups.forEach { (label, list) ->
                        item(key = "header_$label") { SectionHeader(label) }
                        items(list, key = { label + it.id }) { session ->
                            SessionRow(
                                session = session,
                                selected = session.id == currentSessionId,
                                onOpen = { onOpen(session.id) },
                                onTogglePin = { onTogglePin(session.id) },
                                onRename = { renameTarget = session },
                                onDelete = { deleteTarget = session },
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        var title by remember(target.id) { mutableStateOf(target.title) }
        AppDialog(
            title = "重命名对话",
            onDismiss = { renameTarget = null },
            actions = listOf(
                AppDialogAction("取消", onClick = { renameTarget = null }),
                AppDialogAction(
                    label = "保存",
                    enabled = title.isNotBlank(),
                    onClick = {
                        onRename(target.id, title)
                        renameTarget = null
                    },
                    style = AppDialogActionStyle.Primary,
                )
            )
        ) {
            AppTextField(
                value = title,
                onValueChange = { title = it.take(40) },
                label = "对话名称",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    deleteTarget?.let { target ->
        AppDialog(
            title = "删除对话",
            onDismiss = { deleteTarget = null },
            actions = listOf(
                AppDialogAction("取消", onClick = { deleteTarget = null }),
                AppDialogAction(
                    label = "删除",
                    onClick = {
                        onDelete(target.id)
                        deleteTarget = null
                    },
                    style = AppDialogActionStyle.Danger,
                )
            )
        ) {
            Text(
                text = "确定删除「${target.title}」吗？此操作不可恢复。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = "搜索对话内容…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: AssistantSessionSummary,
    selected: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuVisible by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) primary.copy(alpha = 0.12f) else Color.Transparent)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuVisible = true },
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = session.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
            DropdownMenuItem(
                text = { Text(if (session.pinned) "取消置顶" else "置顶") },
                onClick = {
                    menuVisible = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    menuVisible = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    menuVisible = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
internal fun AssistantHeaderIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    background: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (background) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
