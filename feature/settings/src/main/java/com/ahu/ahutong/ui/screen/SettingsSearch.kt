package com.ahu.ahutong.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.SettingsActionRow

/** 一条可被搜索到的设置项；keywords 就是近义词词库。 */
internal class SettingsSearchEntry(
    val title: String,
    val keywords: List<String>,
    val open: () -> Unit
)

/** 标题与近义词都做包含匹配；空查询不返回结果。 */
internal fun searchSettings(
    entries: List<SettingsSearchEntry>,
    query: String
): List<SettingsSearchEntry> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    return entries.filter { entry ->
        entry.title.lowercase().contains(needle) ||
            entry.keywords.any { it.lowercase().contains(needle) }
    }
}

@Composable
internal fun SettingsSearchSheet(
    entries: List<SettingsSearchEntry>,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, entries) { searchSettings(entries, query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AppModalBottomSheet(title = "搜索设置", onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            AppTextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索设置项或关键词",
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
            Spacer(Modifier.height(8.dp))
            if (query.isNotBlank() && results.isEmpty()) {
                Text(
                    text = "没有找到相关设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            results.forEachIndexed { index, entry ->
                SettingsActionRow(
                    title = entry.title,
                    showDivider = index != results.lastIndex,
                    onClick = {
                        onDismiss()
                        entry.open()
                    }
                )
            }
        }
    }
}
