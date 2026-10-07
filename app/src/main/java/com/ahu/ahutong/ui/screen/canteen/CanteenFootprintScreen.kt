package com.ahu.ahutong.ui.screen.canteen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.recharge.analytics.CanteenFootprint
import com.ahu.ahutong.data.recharge.analytics.WindowStat
import com.ahu.ahutong.ui.state.CanteenFootprintViewModel

/*
 * 干饭足迹（必吃榜个人端）：H5 内容页，刻意不走三主题设计系统——
 * 素白底 + 点评橙点睛，锁亮色（年度账单类内容页固定肤色，不随系统暗色）。
 * 配色全部集中在 CanteenPalette；页面自包含在 ui/screen/canteen 包内。
 */
@Composable
fun CanteenFootprintScreen(
    onBack: (() -> Unit)? = null,
    viewModel: CanteenFootprintViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // 极简页头：返回 + 顶部橙色带（点评榜单手法）
        Box(
            modifier = Modifier
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
        }

        when (val s = state) {
            is CanteenFootprintViewModel.UiState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CanteenPalette.accent)
            }

            is CanteenFootprintViewModel.UiState.Error -> Box(
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
                            .clickable { viewModel.refresh() }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            is CanteenFootprintViewModel.UiState.Ready -> FootprintContent(s.footprint)
        }
    }
}

@Composable
private fun FootprintContent(footprint: CanteenFootprint) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // —— 大数字开场 ——
        Text("本学期 · 仅正餐", style = CaptionText, color = CanteenPalette.textSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${footprint.mealCount}", style = HeroNumber, color = CanteenPalette.textPrimary)
            Text(
                " 餐",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                color = CanteenPalette.accent,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(
            "${footprint.totalFen.money()} · 均价 ${footprint.avgFen.money()} · " +
                "覆盖 ${footprint.windowCount} 个窗口",
            style = CaptionText,
            color = CanteenPalette.textSecondary
        )

        Spacer(Modifier.height(24.dp))

        // —— GitHub 式热力格 ——
        if (footprint.weeks.isNotEmpty()) {
            SectionHeader("干饭热力", tail = "一学期 · 每格一天")
            Spacer(Modifier.height(8.dp))
            HeatGrid(footprint.weeks)
            Spacer(Modifier.height(6.dp))
            HeatLegend()
        }

        Spacer(Modifier.height(24.dp))

        // —— 我的窗口榜 ——
        SectionHeader("我的窗口榜", tail = "本学期")
        Spacer(Modifier.height(8.dp))
        if (footprint.topWindows.isEmpty()) {
            Text(
                "还没有带终端码的消费记录，去食堂刷一餐就有了",
                style = CaptionText,
                color = CanteenPalette.textSecondary
            )
        }
        footprint.topWindows.take(10).forEachIndexed { index, window ->
            WindowRow(rank = index + 1, window = window)
            Spacer(Modifier.height(8.dp))
        }

        // —— 徽章区 ——
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (footprint.newWindowsThisWeek > 0) {
                OutlineChip("本周尝新 ×${footprint.newWindowsThisWeek}")
            }
            footprint.cheapestWindow?.let {
                OutlineChip("最省 ${it.avgFen.money()}/餐")
            }
            if (footprint.weekendMealCount > 0) {
                OutlineChip("周末坚守 ×${footprint.weekendMealCount}")
            }
        }
    }
}

/** 7 行（周一~周日）× N 周，一格一天，灰→橙五档。 */
@Composable
private fun HeatGrid(weeks: List<List<Int>>) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf("一", "", "", "四", "", "", "日").forEach {
                Text(
                    it,
                    style = CaptionText,
                    color = CanteenPalette.textSecondary,
                    modifier = Modifier.height(10.dp)
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (week in weeks) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (count in week) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(heatColor(count))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("少", style = CaptionText, color = CanteenPalette.textSecondary)
        Spacer(Modifier.width(4.dp))
        listOf(
            CanteenPalette.heat0, CanteenPalette.heat1, CanteenPalette.heat2,
            CanteenPalette.accent, CanteenPalette.accentDeep
        ).forEach { color ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(9.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text("多", style = CaptionText, color = CanteenPalette.textSecondary)
    }
}

private fun heatColor(count: Int): Color = when {
    count <= 0 -> CanteenPalette.heat0
    count == 1 -> CanteenPalette.heat1
    count == 2 -> CanteenPalette.heat2
    count == 3 -> CanteenPalette.accent
    else -> CanteenPalette.accentDeep
}

@Composable
private fun WindowRow(rank: Int, window: WindowStat) {
    val champion = rank == 1
    val claimed = window.windowName != null
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!claimed) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = CanteenPalette.cardLine,
                            cornerRadius = CornerRadius(12.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect)
                        )
                    }
                } else {
                    Modifier.border(
                        width = if (champion) 1.dp else 0.5.dp,
                        color = if (champion) CanteenPalette.accent else CanteenPalette.cardLine,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            )
            .background(
                // 未标注卡用 drawBehind 画虚线描边，底色必须透明，否则盖住虚线
                if (claimed) {
                    if (champion) CanteenPalette.championBg else Color.White
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RankBadge(rank)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                // 未收录：「榴园一楼 · 77-139 号窗」；已收录：窗口名为主标题，楼层在副标题
                window.windowName ?: "${window.location} · ${window.terminal} 号窗",
                style = BodyText.copy(fontWeight = if (champion) FontWeight.Medium else FontWeight.Normal),
                color = if (claimed) CanteenPalette.textPrimary else Color(0xFF666666)
            )
            Text(
                (if (claimed) "${window.location} · " else "") +
                    "${window.mealCount} 次 · 均价 ${window.avgFen.money()}" +
                    (if (window.currentStreak >= 2) " · 连续 ${window.currentStreak} 天" else "") +
                    (if (!claimed) " · POS 机未标注" else ""),
                style = CaptionText,
                color = if (!claimed) CanteenPalette.accent else CanteenPalette.textSecondary
            )
        }
        if (champion && claimed) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CanteenPalette.accent)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("擂主", style = CaptionText, color = Color.White)
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val (bg, textColor, bordered) = when (rank) {
        1 -> Triple(CanteenPalette.accent, Color.White, false)
        2 -> Triple(CanteenPalette.gold, Color.White, false)
        3 -> Triple(CanteenPalette.bronze, Color.White, false)
        else -> Triple(Color.Transparent, CanteenPalette.textSecondary, true)
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(
                if (bordered) {
                    Modifier.border(1.dp, Color(0xFFC8C6BE), CircleShape)
                } else {
                    Modifier.background(bg)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text("$rank", style = CaptionText.copy(fontWeight = FontWeight.Medium), color = textColor)
    }
}
