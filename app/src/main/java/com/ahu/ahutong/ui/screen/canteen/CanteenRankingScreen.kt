package com.ahu.ahutong.ui.screen.canteen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.canteen.InsightRankingItem
import com.ahu.ahutong.data.canteen.InsightsResponse
import com.ahu.ahutong.ui.state.CanteenRankingViewModel

/**
 * 安大必吃榜（全校，二期）：渲染服务端 /insights 算好的结果，客户端只做展示。
 * 与干饭足迹同视觉语言（素底点评风、锁亮色）。
 * 服务端「暂时没有」的兜底（文档 §4.2）：superlatives 字段为 null 的卡片隐藏；
 * 窗口 name 为 null 显示「未命名窗口」；notes 原文展示。
 */
@Composable
fun CanteenRankingScreen(
    onBack: (() -> Unit)? = null,
    onOpenFootprint: () -> Unit = {},
    viewModel: CanteenRankingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(CanteenPalette.accent)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = CanteenPalette.textPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onBack?.invoke() }
                    .padding(10.dp)
                    .size(24.dp)
            )
            Spacer(Modifier.weight(1f))
            // 个人干饭足迹（子页面入口）
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = "我的干饭足迹",
                tint = CanteenPalette.textPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOpenFootprint() }
                    .padding(10.dp)
                    .size(24.dp)
            )
            // 刷新（绕过端侧缓存）
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "刷新",
                tint = CanteenPalette.textPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { viewModel.refresh() }
                    .padding(10.dp)
                    .size(24.dp)
            )
        }

        when (val s = state) {
            is CanteenRankingViewModel.UiState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CanteenPalette.accent)
            }

            is CanteenRankingViewModel.UiState.Error -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = BodyText, color = CanteenPalette.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "重试",
                        style = BodyText,
                        color = CanteenPalette.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { viewModel.load(s.period) }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            is CanteenRankingViewModel.UiState.Ready -> RankingContent(
                data = s.data,
                currentPeriod = s.period,
                onPeriodChange = { viewModel.load(it) }
            )
        }
    }
}

@Composable
private fun RankingContent(
    data: InsightsResponse,
    currentPeriod: CanteenRankingViewModel.Period,
    onPeriodChange: (CanteenRankingViewModel.Period) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // —— 头部 + 周期切换 ——
        Text(
            "安大必吃榜",
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
            color = CanteenPalette.textPrimary
        )
        Text(
            "${data.period.orEmpty()} · ${data.startDay.orEmpty()} ~ ${data.endDay.orEmpty()}",
            style = CaptionText,
            color = CanteenPalette.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CanteenRankingViewModel.Period.entries.forEach { p ->
                val selected = p == currentPeriod
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) CanteenPalette.accent else Color(0xFFF5F4F0))
                        .clickable { if (!selected) onPeriodChange(p) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        p.label,
                        style = CaptionText,
                        color = if (selected) Color.White else CanteenPalette.textSecondary
                    )
                }
            }
        }

        // —— 置信度标签 ——
        data.confidence?.note?.let { note ->
            Spacer(Modifier.height(10.dp))
            Text(
                note,
                style = CaptionText,
                color = CanteenPalette.textSecondary
            )
        }

        Spacer(Modifier.height(20.dp))

        // —— 擂主海报卡 ——
        data.superlatives?.topWindow?.let { top ->
            ChampionCard(top, data)
            Spacer(Modifier.height(16.dp))
        }

        // —— 榜单 ——
        SectionHeader("窗口排行榜", tail = data.period)
        Spacer(Modifier.height(8.dp))
        val ranking = data.ranking.orEmpty()
        if (ranking.isEmpty()) {
            Text(
                "还在积累数据，吃几顿饭再来看看",
                style = CaptionText,
                color = CanteenPalette.textSecondary
            )
        }
        ranking.forEach { item ->
            RankingRow(item)
            Spacer(Modifier.height(8.dp))
        }

        // —— 食堂榜 ——
        val canteenRanking = data.canteenRanking.orEmpty()
        if (canteenRanking.size > 1) {
            Spacer(Modifier.height(16.dp))
            SectionHeader("最热食堂")
            Spacer(Modifier.height(8.dp))
            canteenRanking.take(5).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}. ${item.canteen}",
                        style = BodyText,
                        color = CanteenPalette.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${item.txns} 餐 · ${((item.share ?: 0.0) * 100).toInt()}%",
                        style = CaptionText,
                        color = CanteenPalette.textSecondary
                    )
                }
            }
        }

        // —— 餐段分布 ——
        val segments = data.segments.orEmpty().filter { it.txns > 0 }
        if (segments.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHeader("餐段分布")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.forEach { seg ->
                    val label = when (seg.segment) {
                        "lunch" -> "午餐"
                        "dinner" -> "晚餐"
                        "breakfast" -> "早餐"
                        else -> seg.segment
                    }
                    OutlineChip("$label ${((seg.share ?: 0.0) * 100).toInt()}%")
                }
            }
        }

        // —— 几点最挤（hourly 时段分布条） ——
        val hourly = data.hourly.orEmpty().filter { it.txns > 0 }
        if (hourly.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            val peak = data.superlatives?.busiestHour
            SectionHeader(
                "几点最挤",
                tail = peak?.let { "${it.hour} 点 · ${((it.share ?: 0.0) * 100).toInt()}%" }
            )
            Spacer(Modifier.height(8.dp))
            val maxTxns = hourly.maxOf { it.txns }.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                hourly.sortedBy { it.hour }.forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${h.hour} 点",
                            style = CaptionText,
                            color = CanteenPalette.textSecondary,
                            modifier = Modifier.width(36.dp)
                        )
                        Box(
                            Modifier
                                .height(8.dp)
                                .fillMaxWidth(h.txns.toFloat() / maxTxns)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (peak?.hour == h.hour) CanteenPalette.accent
                                    else CanteenPalette.heat2
                                )
                        )
                    }
                }
            }
        }

        // —— 之最（null 即隐藏，notes 提示原文展示） ——
        val extras = buildList {
            data.superlatives?.lunchTopWindow?.let { add("午餐冠军" to it) }
            data.superlatives?.dinnerTopWindow?.let { add("晚餐冠军" to it) }
            data.superlatives?.mostConsistent?.let { add("发挥最稳" to it) }
            data.superlatives?.leastPopular?.let { add("沧海遗珠" to it) }
        }
        if (extras.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHeader("之最")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                extras.forEach { (label, item) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = BodyText, color = CanteenPalette.textSecondary)
                        Text(
                            "${item.name ?: "未命名窗口"} · ${item.txns} 餐",
                            style = BodyText,
                            color = CanteenPalette.textPrimary
                        )
                    }
                }
            }
        }

        // —— 服务端说明（样本不足等） ——
        data.notes.orEmpty().forEach { note ->
            Spacer(Modifier.height(8.dp))
            Text(note, style = CaptionText, color = CanteenPalette.textSecondary)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "榜单由全校同学匿名消费计数聚合而成，不含任何个人信息",
            style = CaptionText,
            color = CanteenPalette.textSecondary
        )
    }
}

/** 擂主海报卡：浅橙底 + 橙描边 + 「擂主」胶囊（点评榜手法）。 */
@Composable
private fun ChampionCard(top: InsightRankingItem, data: InsightsResponse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CanteenPalette.championBg)
            .border(1.dp, CanteenPalette.accent, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "本周擂主",
                style = CaptionText,
                color = CanteenPalette.accent
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CanteenPalette.accent)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("擂主", style = CaptionText, color = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            top.name ?: "${top.terminal} 号窗",
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),
            color = CanteenPalette.textPrimary
        )
        Text(
            listOfNotNull(top.canteen, top.floor).joinToString(" · ").ifBlank { "待踩点确认" },
            style = CaptionText,
            color = CanteenPalette.textSecondary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${top.txns} 人次" + (top.avgCentsPerTxn?.let { " · 人均 ¥%.1f".format(it / 100.0) } ?: ""),
            style = BodyText,
            color = CanteenPalette.accent
        )
    }
}

@Composable
private fun RankingRow(item: InsightRankingItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankCircle(item.rank)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name ?: "${item.terminal} 号窗（未命名）",
                style = BodyText,
                color = CanteenPalette.textPrimary
            )
            Text(
                listOfNotNull(item.canteen, item.floor).joinToString(" · ").ifBlank { "—" },
                style = CaptionText,
                color = CanteenPalette.textSecondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${item.txns} 人次", style = BodyText, color = CanteenPalette.textPrimary)
            item.avgCentsPerTxn?.let {
                Text(
                    "人均 ¥%.1f".format(it / 100.0),
                    style = CaptionText,
                    color = CanteenPalette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun RankCircle(rank: Int) {
    val (bg, fg) = when (rank) {
        1 -> CanteenPalette.accent to Color.White
        2 -> CanteenPalette.gold to Color.White
        3 -> CanteenPalette.bronze to Color.White
        else -> Color(0xFFF0EFE9) to CanteenPalette.textSecondary
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text("$rank", style = CaptionText.copy(fontWeight = FontWeight.Medium), color = fg)
    }
}
