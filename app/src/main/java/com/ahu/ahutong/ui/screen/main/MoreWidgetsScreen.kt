package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.plugin.PluginManagerSection
import com.ahu.ahutong.ui.plugin.PluginRegistry
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetSpec
import com.ahu.ahutong.ui.screen.main.home.WidgetCategory
import com.kyant.monet.n1
import com.kyant.monet.withNight

private val TileMinWidth = 150.dp
private val TileGap = 12.dp
private val RowGap = 8.dp

/**
 * 「全部小工具」页：分类分组 + 自适应网格。
 *
 * 不用 LazyVerticalGrid：AppPageScaffold 的 content 分支本身是 verticalScroll 容器，
 * 嵌套懒列表会撞无限高度约束。这里按可用宽度现算列数后 chunked 成 Row，
 * 列宽与 GridCells.Adaptive(150dp) 等价；残行用 Spacer(weight) 补齐、左对齐。
 */
@Composable
fun MoreWidgetsScreen(
    navController: NavHostController
) {
    // 订阅插件注册表：装/卸插件后分组与入口列表自动重组
    val pluginsState by PluginRegistry.plugins.collectAsState()
    val grouped = remember(pluginsState) {
        // 「全部小工具」= 展示全部组件（含已上主页的），不做减法；先既有过滤再按 category 分组
        val all = HomeWidgetRegistry.availableWidgets(true)
        WidgetCategory.entries.mapNotNull { category ->
            val itemsInCategory = all.filter { it.category == category }
            if (itemsInCategory.isEmpty()) null else category to itemsInCategory
        }
    }

    AppPageScaffold(
        title = "全部小工具",
        onBack = { navController.popBackStack() },
        bottomPadding = 48.dp,
        content = {
            // 水平内边距由壳统一提供，页面内容不再自带
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
                for ((category, specs) in grouped) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = 50.n1 withNight 70.n1,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    WidgetTileRows(specs, onOpen = { navController.navigate(it.route) })
                }
                // 插件管理（安装/卸载 .ahup）
                Box(Modifier.padding(top = 12.dp)) { PluginManagerSection() }
                // 桌面课表微件卡（研究生不显示，组件内部自判）
                Box(Modifier.padding(top = 12.dp)) { DesktopScheduleWidgetCard() }
            }
        }
    )
}

/** 自适应网格行：列数按可用宽度现算，残行保持普通列宽、左对齐。 */
@Composable
private fun WidgetTileRows(specs: List<HomeWidgetSpec>, onOpen: (HomeWidgetSpec) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 与 GridCells.Adaptive(minSize) 同一公式
        val columns = ((maxWidth + TileGap) / (TileMinWidth + TileGap)).toInt().coerceAtLeast(1)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
            specs.chunked(columns).forEach { rowSpecs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TileGap)
                ) {
                    rowSpecs.forEach { spec ->
                        CategorizedToolCard(
                            spec = spec,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpen(spec) }
                        )
                    }
                    // 每个 Spacer 占一列宽 → 残行卡片不拉伸
                    repeat(columns - rowSpecs.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
