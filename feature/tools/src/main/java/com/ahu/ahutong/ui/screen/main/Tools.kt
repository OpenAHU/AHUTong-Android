package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ahu.ahutong.feature.tools.R
import com.ahu.ahutong.ui.components.AhuHeaderIconButton
import com.ahu.ahutong.ui.components.AhuInsetCard
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.components.AhuToolItem
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens

@Composable
fun Tools(
    navController: NavHostController,
    homeEditEnabled: Boolean = false,
    onEditHome: () -> Unit = {},
    placedWidgetIds: Set<String> = emptySet(),
    onPinScheduleWidget: () -> Unit = {},
) {
    var homeWidgetIds by remember(placedWidgetIds) { mutableStateOf(placedWidgetIds) }
    LaunchedEffect(placedWidgetIds) {
        homeWidgetIds = placedWidgetIds
    }
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            if (backStackEntry.destination.route == "tools") {
                homeWidgetIds = placedWidgetIds
            }
        }
    }

    AhuScreen(clearBottomNav = true) {
        AhuPageHeader(
            title = "小工具",
            actions = {
                if (homeEditEnabled) {
                    AhuHeaderIconButton(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "编辑首页",
                        onClick = {
                            onEditHome()
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                }
            },
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AhuDimens.ContentHorizontal),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeWidgetRegistry.widgets
                .filter { it.id !in homeWidgetIds }
                .forEach { widget ->
                    AhuToolItem(
                        title = widget.title,
                        iconResId = widget.iconId,
                        tint = widget.tint,
                        onClick = { navController.navigate(widget.route) },
                    )
                }
        }

        AhuInsetCard(
            containerColor = AhuColors.cardStrong,
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "添加桌面课表微件",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            Image(
                painter = painterResource(id = R.mipmap.schedule_widget_prev),
                contentDescription = "桌面课表微件",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            AhuPrimaryButton(
                text = "添加",
                onClick = onPinScheduleWidget,
                modifier = Modifier.padding(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
