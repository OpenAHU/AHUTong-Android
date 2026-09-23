package com.ahu.ahutong.ui.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.AppStateCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 轻量 MD 渲染器（隐私政策专用语法子集）。
 * lazy=true 全屏页用 LazyColumn；弹窗内必须 lazy=false（AppDialog 的滚动容器给无限高度约束）。
 * 全部颜色走 MaterialTheme.colorScheme，暗色/主题色自动跟随。
 */
@Composable
fun AppMarkdown(
    markdown: String,
    modifier: Modifier = Modifier,
    lazy: Boolean = true
) {
    val blocks by produceState<List<MarkdownBlock>?>(null, markdown) {
        value = withContext(Dispatchers.Default) { MarkdownParser.parse(markdown) }
    }
    val ready = blocks
    if (ready == null) {
        AppStateCard.Loading(message = "加载中…")
        return
    }
    if (lazy) {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(ready) { block -> MarkdownBlockContent(block) }
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ready.forEach { block -> MarkdownBlockContent(block) }
        }
    }
}

@Composable
private fun MarkdownBlockContent(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.Heading -> Text(
            text = block.text,
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                top = if (block.level <= 2) 12.dp else 6.dp,
                bottom = 2.dp
            )
        )
        is MarkdownBlock.Paragraph -> InlineRunsText(block.runs)
        is MarkdownBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEachIndexed { index, runs ->
                Row {
                    Text("• ", style = MaterialTheme.typography.bodyMedium)
                    InlineRunsText(runs, modifier = Modifier.weight(1f))
                }
            }
        }
        is MarkdownBlock.OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.items.forEachIndexed { index, runs ->
                Row {
                    Text("${index + 1}. ", style = MaterialTheme.typography.bodyMedium)
                    InlineRunsText(runs, modifier = Modifier.weight(1f))
                }
            }
        }
        is MarkdownBlock.Table -> MarkdownTable(block)
    }
}

@Composable
private fun InlineRunsText(runs: List<InlineRun>, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        runs.forEach { run ->
            when (run) {
                is InlineRun.Text -> append(run.text)
                is InlineRun.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(run.text)
                }
                is InlineRun.Link -> {
                    pushStringAnnotation("url", run.url)
                    withStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    ) {
                        append(run.text)
                    }
                    pop()
                }
            }
        }
    }
    androidx.compose.foundation.text.ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("url", offset, offset)
                .firstOrNull()?.let { uriHandler.openUri(it.item) }
        }
    )
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table) {
    val columnCount = table.header.size.coerceAtLeast(1)
    // 列宽策略：每列至少 96dp 放得下 → weight 等宽铺满；放不下 → 固定 112dp 列宽 + 横滚。
    // 注意：horizontalScroll 给无限宽度约束，内部 Row 绝不可用 weight。
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val fits = maxWidth / columnCount >= 96.dp
        Column(
            modifier = if (fits) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.horizontalScroll(rememberScrollState())
            }
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                table.header.forEach { cell ->
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = (if (fits) Modifier.weight(1f) else Modifier.width(112.dp))
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )
            table.rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    (0 until columnCount).forEach { c ->
                        Text(
                            text = row.getOrNull(c).orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = (if (fits) Modifier.weight(1f) else Modifier.width(112.dp))
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
