package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import com.ahu.ahutong.ui.screen.main.home.WidgetCategory
import com.kyant.monet.n1
import com.kyant.monet.withNight

/**
 * 「全部小工具」页：分类分组 + LazyVerticalGrid 自适应网格。
 *
 * - GridCells.Adaptive(150dp)：列数不封顶、随屏宽自适应，列宽均分 → 每行严格两边对齐；
 * - 分类按枚举声明序渲染，过滤后为空的分类整段跳过（如研究生无"学业教务"）；
 * - 残行卡片保持普通列宽、左对齐，不做整行拉伸；
 * - 标题栏 / 插件管理 / 桌面课表卡为跨全行 item，顺序保持旧版。
 */
@Composable
fun MoreWidgetsScreen(
    navController: NavHostController,
    homeEditEnabled: Boolean = false,
    onEditHome: () -> Unit = {}
) {
    // 订阅插件注册表：装/卸插件后分组与入口列表自动重组
    val pluginsState by com.ahu.ahutong.ui.plugin.PluginRegistry.plugins.collectAsState()
    val grouped = remember(pluginsState) {
        // 「全部小工具」= 展示全部组件（含已上主页的），不做减法；先既有过滤再按 category 分组
        val all = HomeWidgetRegistry.availableWidgets(true)
        WidgetCategory.entries.mapNotNull { category ->
            val itemsInCategory = all.filter { it.category == category }
            if (itemsInCategory.isEmpty()) null else category to itemsInCategory
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        // 标题栏（跨全行）
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    text = "全部小工具",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium
                )
                if (homeEditEnabled) {
                    IconButton(
                        onClick = {
                            onEditHome()
                            navController.navigate("home") {
                                popUpTo("home") {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "编辑首页"
                        )
                    }
                }
            }
        }

        // 分类分组卡片区
        for ((category, specs) in grouped) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header:" + category.name) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = 50.n1 withNight 70.n1,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }
            for (spec in specs) {
                // 残行卡保持普通列宽、左对齐，不做整行拉伸
                item(key = spec.id) {
                    CategorizedToolCard(
                        spec = spec,
                        onClick = { navController.navigate(spec.route) }
                    )
                }
            }
        }

        // 插件管理（安装/卸载 .ahup）
        item(span = { GridItemSpan(maxLineSpan) }, key = "plugin_manager") {
            androidx.compose.foundation.layout.Box(Modifier.padding(top = 12.dp)) {
                com.ahu.ahutong.ui.plugin.PluginManagerSection()
            }
        }
        // 必吃榜上传自检：仅 debug 构建挂出（上传是静默的，失败界面上看不见）
        if (com.ahu.ahutong.BuildConfig.DEBUG) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "canteen_upload_diag") {
                androidx.compose.foundation.layout.Box(Modifier.padding(top = 12.dp)) {
                    com.ahu.ahutong.ui.screen.canteen.CanteenUploadDiagnosticsCard()
                }
            }
        }
        // 桌面课表微件卡（研究生不显示，组件内部自判）
        item(span = { GridItemSpan(maxLineSpan) }, key = "desktop_widget") {
            androidx.compose.foundation.layout.Box(Modifier.padding(top = 12.dp)) {
                DesktopScheduleWidgetCard()
            }
        }
    }
}
