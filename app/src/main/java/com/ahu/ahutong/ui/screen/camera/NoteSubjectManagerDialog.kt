package com.ahu.ahutong.ui.screen.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.notes.NoteStore
import com.ahu.ahutong.data.notes.NoteSubject
import com.ahu.ahutong.data.notes.UNFILED_SUBJECT_ID
import com.ahu.ahutong.data.notes.UNFILED_SUBJECT_NAME
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.haptics.HapticIconButton as IconButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Subject management remains available even when the gallery contains no photos. */
@Composable
internal fun NoteSubjectManagerDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var subjects by remember { mutableStateOf<List<NoteSubject>>(emptyList()) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NoteSubject?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<NoteSubject?>(null) }

    LaunchedEffect(refreshTick) {
        try {
            val (items, photoCounts) = withContext(Dispatchers.IO) {
                NoteStore.subjects() to NoteStore.photos().groupingBy { it.subjectId }.eachCount()
            }
            subjects = items.filter { it.id != UNFILED_SUBJECT_ID }
            counts = photoCounts
            loaded = true
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = "加载科目失败，请关闭后重试"
        }
    }

    fun change(failureMessage: String, action: () -> Unit, afterSave: () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                afterSave()
                refreshTick++
                onChanged()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = failureMessage
            } finally {
                busy = false
            }
        }
    }

    if (showEditor) {
        val trimmed = name.trim()
        val nameError = when {
            trimmed == UNFILED_SUBJECT_NAME -> "“未归档”是系统科目，请使用其他名称"
            subjects.any { it.id != editing?.id && it.name == trimmed } -> "已存在同名科目"
            else -> null
        }
        AppDialog(
            title = if (editing == null) "新建科目" else "重命名科目",
            onDismiss = { if (!busy) { showEditor = false; error = null } },
            actions = listOf(
                AppDialogAction("取消", enabled = !busy, onClick = { showEditor = false; error = null }),
                AppDialogAction("保存", style = AppDialogActionStyle.Primary,
                    enabled = !busy && trimmed.isNotEmpty() && nameError == null,
                    onClick = {
                        val subject = editing
                        change("保存科目失败，请重试", action = {
                            if (subject == null) NoteStore.ensureSubject(trimmed)
                            else check(NoteStore.renameSubject(subject.id, trimmed))
                        }, afterSave = { showEditor = false })
                    })
            ),
            content = {
                AppTextField(value = name, onValueChange = { name = it; error = null },
                    label = "科目名称", enabled = !busy, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                (nameError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        )
    } else if (deleting != null) {
        val subject = deleting!!
        val count = counts[subject.id] ?: 0
        AppDialog(
            title = "删除科目？",
            subtitle = if (count == 0) "“${subject.name}”没有笔记，将删除这个空文件夹。"
                else "“${subject.name}”中的 $count 张笔记将移到“未归档”，照片会保留。",
            onDismiss = { if (!busy) { deleting = null; error = null } },
            actions = listOf(
                AppDialogAction("取消", enabled = !busy, onClick = { deleting = null; error = null }),
                AppDialogAction("删除", style = AppDialogActionStyle.Danger, enabled = !busy,
                    onClick = {
                        change("删除科目失败，笔记已保留，请重试",
                            action = { NoteStore.deleteSubject(subject.id) },
                            afterSave = { deleting = null })
                    })
            ),
            content = error?.let { message ->
                { Text(message, color = MaterialTheme.colorScheme.error) }
            }
        )
    } else {
        AppDialog(
            title = "管理科目",
            onDismiss = { if (!busy) onDismiss() },
            contentScrollable = true,
            actions = listOf(
                AppDialogAction("新建科目", style = AppDialogActionStyle.Primary,
                    enabled = loaded && !busy, onClick = {
                        editing = null
                        name = ""
                        error = null
                        showEditor = true
                    }),
                AppDialogAction("完成", enabled = !busy, onClick = onDismiss)
            ),
            content = {
                Text("删除科目时，其中的笔记会移到“未归档”。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                when {
                    !loaded -> Text("正在加载科目…")
                    subjects.isEmpty() -> Text("还没有科目，点击“新建科目”添加")
                    else -> subjects.forEach { subject ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(subject.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge)
                                Text("${counts[subject.id] ?: 0} 张笔记",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(enabled = !busy, onClick = {
                                editing = subject
                                name = subject.name
                                error = null
                                showEditor = true
                            }) { Icon(Icons.Rounded.Edit, contentDescription = "重命名${subject.name}") }
                            IconButton(enabled = !busy, onClick = { deleting = subject; error = null }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除${subject.name}",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}
