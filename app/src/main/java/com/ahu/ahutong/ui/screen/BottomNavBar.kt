package com.ahu.ahutong.ui.screen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ahu.ahutong.R
import com.ahu.ahutong.ui.components.LiquidBottomTab
import com.ahu.ahutong.ui.components.LiquidBottomTabs
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongDockState
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongSubTab
import com.kyant.backdrop.Backdrop

@Composable
fun BoxScope.BottomNavBar(
    navController: NavHostController,
    backdrop: Backdrop
) {
    if (isRadiantUi) {
        RadiantBottomNavBar(navController, backdrop)
    } else {
        ClassicBottomNavBar(navController, backdrop)
    }
}

private fun NavController.navigatePreservingHome(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    navigate(route) {
        popUpTo("home") { inclusive = false }
        launchSingleTop = true
    }
}

// ==================== 曜光版：学习通日历提级为第三 tab（日程/课程轮换） ====================

private data class RadiantDestination(
    val route: String,
    val label: String,
    val icon: Painter
)

@Composable
private fun BoxScope.RadiantBottomNavBar(
    navController: NavHostController,
    backdrop: Backdrop
) {
    val onXuexiaotongSub = XuexiaotongDockState.tab == XuexiaotongSubTab.SCHEDULE
    val destinations = listOf(
        RadiantDestination("home", "主页", painterResource(R.drawable.ic_nav_home)),
        RadiantDestination("schedule", "课表", painterResource(R.drawable.ic_nav_schedule)),
        RadiantDestination(
            "xuexiaotong",
            if (onXuexiaotongSub) "日程" else "课程",
            painterResource(if (onXuexiaotongSub) R.drawable.ic_nav_plan else R.drawable.ic_nav_degree_hat)
        ),
        RadiantDestination("settings", "设置", painterResource(R.drawable.ic_nav_settings))
    )

    val currentRoute by navController.currentBackStackEntryAsState()
    val selectedRoute = currentRoute?.destination?.route
    if (selectedRoute !in destinations.map { it.route }) return

    fun onTabTapped(route: String) {
        if (route == navController.currentBackStackEntry?.destination?.route) {
            if (route == "xuexiaotong") XuexiaotongDockState.toggle()
            return
        }
        navController.navigatePreservingHome(route)
    }

    fun navigateToTab(route: String) {
        navController.navigatePreservingHome(route)
    }

    if (LocalIsLiquidGlassEnabled.current) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            LiquidBottomTabs(
                selectedTabIndex = {
                    destinations.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
                },
                onTabSelected = { index ->
                    navigateToTab(destinations[index].route)
                },
                onCurrentTabTapped = {
                    selectedRoute?.let { onTabTapped(it) }
                },
                backdrop = backdrop,
                tabsCount = destinations.size,
                modifier = Modifier.padding(horizontal = 36.dp)
            ) {
                destinations.forEach { destination ->
                    val selected = selectedRoute == destination.route
                    LiquidBottomTab(
                        onClick = { onTabTapped(destination.route) }
                    ) {
                        Icon(
                            painter = destination.icon,
                            contentDescription = destination.label
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    } else {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            destinations.forEach { destination ->
                val selected = selectedRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabTapped(destination.route) },
                    icon = {
                        Icon(
                            painter = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

// ==================== 经典版：小工具第三 tab（原样式） ====================

private data class ClassicDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val classicDestinations = listOf(
    ClassicDestination("home", "主页", Icons.Outlined.Home),
    ClassicDestination("schedule", "课表", Icons.Outlined.TableChart),
    ClassicDestination("tools", "小工具", Icons.Outlined.Build),
    ClassicDestination("settings", "设置", Icons.Outlined.Settings)
)

@Composable
private fun BoxScope.ClassicBottomNavBar(
    navController: NavHostController,
    backdrop: Backdrop
) {
    val currentRoute by navController.currentBackStackEntryAsState()
    val selectedRoute = currentRoute?.destination?.route
    if (selectedRoute !in classicDestinations.map { it.route }) return

    if (LocalIsLiquidGlassEnabled.current) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            LiquidBottomTabs(
                selectedTabIndex = {
                    classicDestinations.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
                },
                onTabSelected = { index ->
                    navController.navigatePreservingHome(classicDestinations[index].route)
                },
                backdrop = backdrop,
                tabsCount = classicDestinations.size,
                modifier = Modifier.padding(horizontal = 36.dp)
            ) {
                classicDestinations.forEach { destination ->
                    val selected = selectedRoute == destination.route
                    LiquidBottomTab(
                        onClick = {
                            navController.navigatePreservingHome(destination.route)
                        }
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    } else {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            classicDestinations.forEach { destination ->
                val selected = selectedRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { navController.navigatePreservingHome(destination.route) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}