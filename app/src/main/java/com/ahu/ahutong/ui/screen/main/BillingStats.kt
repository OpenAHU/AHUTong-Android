package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.recharge.analytics.CardAnalyticsSummary
import com.ahu.ahutong.data.recharge.analytics.CategoryStat
import com.ahu.ahutong.data.recharge.analytics.MerchantStat
import com.ahu.ahutong.data.recharge.analytics.TransactionItem
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.state.BillingStatsViewModel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.util.Locale

/**
 * 账单统计（三级页面）：本月消费分析。
 * 信息架构借自 Ahu_Plus 分析页；视觉全部走工程组件与 monet 语义色。
 */
@Composable
fun BillingStats(
    onBack: () -> Unit,
    viewModel: BillingStatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var detailDay by remember { mutableStateOf<String?>(null) }

    AppPageScaffold(
        title = "账单统计",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        lazyContent = {
        when (val s = state) {
            is BillingStatsViewModel.StatsState.Loading -> {
                item { AppStateCard.Loading(message = "正在分析本月账单…") }
            }
            is BillingStatsViewModel.StatsState.Error -> {
                item { AppStateCard.Error(message = s.message, onRetry = { viewModel.refresh() }) }
            }
            is BillingStatsViewModel.StatsState.Ready -> {
                val summary = s.report.currentMonth
                if (summary == null || summary.expenseCount == 0) {
                    item {
                        AppStateCard.Empty(
                            message = "本月暂无消费记录",
                            subtitle = "有消费后这里会生成统计分析"
                        )
                    }
                } else {
                    item { StatsOverviewCard(summary) }
                    item {
                        StatsTrendChart(
                            summary = summary,
                            onDayClick = { detailDay = it }
                        )
                    }
                    item { StatsFoodSplit(summary) }
                    item { StatsCanteenRank(summary) }
                    item { StatsMealDistribution(summary) }
                    item { StatsNonCanteenCategories(summary) }
                    item { StatsMerchantTop(summary) }
                    item { StatsHighDays(summary, onDayClick = { detailDay = it }) }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "分析引擎由 yoki-Clark 提供支持",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        }
    )

    detailDay?.let { day ->
        val summary = (state as? BillingStatsViewModel.StatsState.Ready)?.report?.currentMonth
        StatsDayDetailDialog(
            day = day,
            items = summary?.transactionsByDay?.get(day).orEmpty(),
            onDismiss = { detailDay = null }
        )
    }
}

/* ==================== 汇总卡 ==================== */


@Composable
private fun StatsOverviewCard(summary: CardAnalyticsSummary) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatsKpi("总支出", "¥${fenToYuan(summary.totalExpense)}")
            StatsKpi("笔数", "${summary.expenseCount}")
            StatsKpi("日均", "¥${fenToYuan(summary.dailyAvg)}")
            StatsKpi("食堂占比", "${(summary.canteenShare * 100).toInt()}%")
        }
    }
}

@Composable
private fun StatsKpi(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = 40.a1 withNight 75.a1
        )
    }
}

/* ==================== 平滑趋势折线（Canvas 手绘，移植 smoothPath） ==================== */

@Composable
private fun StatsTrendChart(summary: CardAnalyticsSummary, onDayClick: (String) -> Unit) {
    val trend = summary.dailyTrend
    if (trend.isEmpty()) return
    val maxExpense = trend.maxOf { it.totalExpense }.coerceAtLeast(1)
    val lineColor = 50.a1
    val fillBrush = Brush.verticalGradient(
        listOf(50.a1.copy(alpha = 0.25f), 50.a1.copy(alpha = 0f))
    )
    StatsSection(title = "本月支出趋势", note = "以凌晨 4 点为日界") {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics {
                    contentDescription = "本月支出趋势图，最高日支出 ${fenToYuan(maxExpense)} 元"
                }
        ) {
            val width = size.width
            val height = size.height
            val stepX = if (trend.size > 1) width / (trend.size - 1) else width
            val points = trend.mapIndexed { index, point ->
                Offset(
                    x = stepX * index,
                    y = height - (point.totalExpense.toFloat() / maxExpense) * (height * 0.85f) - height * 0.05f
                )
            }
            if (points.isEmpty()) return@Canvas
            val path = smoothPath(points)
            // 渐变填充（闭合到 x 轴）
            val fillPath = Path().apply {
                addPath(path)
                lineTo(points.last().x, height)
                lineTo(points.first().x, height)
                close()
            }
            drawPath(fillPath, brush = fillBrush)
            drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${trend.size}日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 二次贝塞尔平滑：相邻点中点作端点、真实点作控制点（不过冲）。 */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 2) {
        path.lineTo(points.last().x, points.last().y)
        return path
    }
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val mid = Offset((previous.x + current.x) / 2f, (previous.y + current.y) / 2f)
        path.quadraticTo(previous.x, previous.y, mid.x, mid.y)
    }
    val beforeLast = points[points.lastIndex - 1]
    val last = points.last()
    path.quadraticTo(beforeLast.x, beforeLast.y, last.x, last.y)
    return path
}

/* ==================== 分区行（通用：名称 + 金额 + 占比条） ==================== */

@Composable
private fun StatsShareRow(
    name: String,
    amountFen: Long,
    count: Int,
    share: Double,
    emphasized: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "¥${fenToYuan(amountFen)} · ${count}笔",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(share * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = 40.a1 withNight 75.a1
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(92.n1 withNight 25.n1)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(50.a1)
            )
        }
    }
}

@Composable
private fun StatsSection(
    title: String,
    note: String? = null,
    content: @Composable () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

/* ==================== 各分区 ==================== */

@Composable
private fun StatsFoodSplit(summary: CardAnalyticsSummary) {
    StatsSection(title = "食堂 / 非食堂") {
        summary.splitRows.forEach { row ->
            StatsShareRow(row.name, row.totalAmount, row.count, row.share, emphasized = true)
        }
    }
}

@Composable
private fun StatsCanteenRank(summary: CardAnalyticsSummary) {
    if (summary.canteenStats.isEmpty()) return
    StatsSection(title = "各食堂排行") {
        summary.canteenStats.forEach { stat ->
            StatsShareRow(stat.name, stat.totalAmount, stat.count, stat.share)
        }
    }
}

@Composable
private fun StatsMealDistribution(summary: CardAnalyticsSummary) {
    if (summary.mealStats.isEmpty()) return
    StatsSection(title = "餐点分布", note = "仅食堂消费") {
        summary.mealStats.forEach { stat: CategoryStat ->
            StatsShareRow(stat.name, stat.totalAmount, stat.count, stat.share)
        }
    }
}

@Composable
private fun StatsNonCanteenCategories(summary: CardAnalyticsSummary) {
    if (summary.nonCanteenCategories.isEmpty()) return
    StatsSection(title = "非食堂分类") {
        summary.nonCanteenCategories.forEach { stat ->
            StatsShareRow(stat.name, stat.totalAmount, stat.count, stat.share)
        }
    }
}

@Composable
private fun StatsMerchantTop(summary: CardAnalyticsSummary) {
    if (summary.nonCanteenMerchants.isEmpty()) return
    StatsSection(title = "非食堂商户 Top${summary.nonCanteenMerchants.size}") {
        summary.nonCanteenMerchants.forEach { stat: MerchantStat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stat.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "¥${fenToYuan(stat.totalAmount)} · ${stat.count}笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatsHighDays(summary: CardAnalyticsSummary, onDayClick: (String) -> Unit) {
    if (summary.highDays.isEmpty()) return
    StatsSection(title = "高消费日 Top${summary.highDays.size}", note = "点按看明细") {
        summary.highDays.forEach { point ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDayClick(point.date) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = point.date.substring(5).replace('-', '月') + "日",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "¥${fenToYuan(point.totalExpense)} · ${point.txnCount}笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ==================== 日明细弹窗 ==================== */

@Composable
private fun StatsDayDetailDialog(
    day: String,
    items: List<TransactionItem>,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = "${day.substring(5).replace('-', '月')}日明细",
        onDismiss = onDismiss,
        contentScrollable = true,
        actions = listOf(AppDialogAction("关闭", onClick = onDismiss)),
        content = {
            if (items.isEmpty()) {
                Text("当日无消费记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.merchant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.category} · ${item.time.substringAfter(' ')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "¥${fenToYuan(item.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = 40.a1 withNight 75.a1
                    )
                }
            }
        }
    )
}

/* ==================== 工具 ==================== */

private fun fenToYuan(fen: Long): String =
    String.format(Locale.CHINA, "%d.%02d", fen / 100, fen % 100)
