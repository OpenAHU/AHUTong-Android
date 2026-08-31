package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

/** 二级页搜索态的交互状态。 */
data class SecondarySearchState(
    val query: String,
    val visible: Boolean,
    val placeholder: String = "输入城市名，如 合肥",
    val onQueryChange: (String) -> Unit,
    val onClose: () -> Unit,
    val onSubmit: () -> Unit
)

/**
 * 统一的二级页面脚手架。
 *
 * RadiantUI：背景 + 顶部固定悬浮标题栏（正常态标题栏 / 搜索态搜索栏），内容区在标题栏
 * 下滚动、可从其渐变遮罩下穿过。Original / Liquid Glass：完整还原原始观感——整页滚动、
 * 标题栏随内容滚动、搜索态内联在标题行。
 *
 * 两种风格的结构差异全部收敛在本组件；后续页面只需传标题、按钮、搜索态与正文。以后删除
 * Original/Liquid Glass 时，删掉本文件的 else 分支即可，所有页面自动只剩 Radiant。
 */
@Composable
fun SecondaryPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    actions: List<TrailingAction> = emptyList(),
    subtitle: String? = null,
    search: SecondarySearchState? = null,
    trailingContent: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    contentEdgeToEdge: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    if (isRadiantUi) {
        RadiantScaffold(title, modifier, actions, subtitle, search, trailingContent, contentEdgeToEdge, content)
    } else {
        ClassicScaffold(title, modifier, actions, subtitle, search, content)
    }
}

@Composable
private fun RadiantScaffold(
    title: String,
    modifier: Modifier,
    actions: List<TrailingAction>,
    subtitle: String?,
    search: SecondarySearchState?,
    trailingContent: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)?,
    contentEdgeToEdge: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(96.n1 withNight 10.n1)
    ) {
        if (search?.visible == true) {
            RadiantSearchHeader(search, Modifier.align(Alignment.TopCenter).zIndex(20f))
        } else {
            SecondaryPageHeader(
                title = title,
                actions = actions,
                subtitle = subtitle,
                trailingContent = trailingContent,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(20f)
            )
        }
        if (contentEdgeToEdge) {
            // 内容自绘滚动/边距/系统栏：不设外置顶部占位，正文可向上穿入半透标题栏之下；
            // 滚动到顶时的停靠占位由各页面滚动容器通过 contentPadding / 顶部占位自行提供
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                content()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(top = if (subtitle != null) 76.dp else 72.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun RadiantSearchHeader(
    search: SecondarySearchState,
    modifier: Modifier = Modifier
) {
    val headerBg = if (LocalIsLiquidGlassEnabled.current) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surface
    }
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
                .height(60.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = search.onClose,
                modifier = Modifier.padding(start = 2.dp, end = 4.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "关闭搜索",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            OutlinedTextField(
                value = search.query,
                onValueChange = search.onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                singleLine = true,
                placeholder = { Text(search.placeholder) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = 0.n1 withNight 100.n1,
                    unfocusedTextColor = 0.n1 withNight 100.n1,
                    cursorColor = 90.a1 withNight 90.a1
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search.onSubmit() }),
                trailingIcon = {
                    if (search.query.isNotEmpty()) {
                        IconButton(onClick = { search.onQueryChange("") }) {
                            Icon(Icons.Default.Close, "清空", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    } else {
                        IconButton(onClick = search.onSubmit) {
                            Icon(Icons.Default.Search, "搜索", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ClassicScaffold(
    title: String,
    modifier: Modifier,
    actions: List<TrailingAction>,
    subtitle: String?,
    search: SecondarySearchState?,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (search?.visible == true) {
                IconButton(onClick = search.onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "关闭搜索")
                }
                OutlinedTextField(
                    value = search.query,
                    onValueChange = search.onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(search.placeholder) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = 0.n1 withNight 100.n1,
                        unfocusedTextColor = 0.n1 withNight 100.n1,
                        cursorColor = 90.a1 withNight 90.a1
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search.onSubmit() }),
                    trailingIcon = {
                        if (search.query.isNotEmpty()) {
                            IconButton(onClick = { search.onQueryChange("") }) {
                                Icon(Icons.Default.Close, "清空")
                            }
                        } else {
                            IconButton(onClick = search.onSubmit) {
                                Icon(Icons.Default.Search, "搜索")
                            }
                        }
                    }
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    actions.forEach { action ->
                        IconButton(onClick = action.onClick) {
                            Icon(action.icon, action.contentDescription)
                        }
                    }
                }
            }
        }

        content()
    }
}