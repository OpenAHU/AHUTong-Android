package com.ahu.ahutong.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.AppHeaderIconButton
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsSection
import com.kyant.backdrop.Backdrop

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

/** 设置搜索：搜索框固定在顶部，结果在剩余空间滚动；键盘弹出时整体避让。 */
@Composable
internal fun SettingsSearchPage(
    entries: List<SettingsSearchEntry>,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, entries) { searchSettings(entries, query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppHeaderIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                backdrop = backdrop
            )
            AppTextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索设置项或关键词",
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (query.isNotBlank() && results.isEmpty()) {
                Text(
                    text = "没有找到相关设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            if (results.isNotEmpty()) {
                SettingsSection(title = null, backdrop = backdrop) {
                    results.forEachIndexed { index, entry ->
                        SettingsActionRow(
                            title = entry.title,
                            showDivider = index != results.lastIndex,
                            onClick = {
                                entry.open()
                                onBack()
                            }
                        )
                    }
                }
            }
        }
    }
}
