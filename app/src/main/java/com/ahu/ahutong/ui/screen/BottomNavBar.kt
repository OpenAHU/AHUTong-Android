package com.ahu.ahutong.ui.screen

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar as MaterialNavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.R
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.components.LiquidBottomTab
import com.ahu.ahutong.ui.components.LiquidBottomTabs
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongDockState
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongSubTab
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem as MiuixNavigationItem

private data class BottomDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private data class RadiantDestination(
    val route: String,
    val label: String,
    @param:DrawableRes val iconId: Int
)

private val classicDestinations = listOf(
    BottomDestination("home", "主页", Icons.Filled.Home, Icons.Outlined.Home),
    BottomDestination("schedule", "课表", Icons.Filled.TableChart, Icons.Outlined.TableChart),
    BottomDestination("tools", "小工具", Icons.Filled.Build, Icons.Outlined.Build),
    BottomDestination("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun BoxScope.BottomNavBar(
    backdrop: Backdrop,
    selectedRoute: String?,
    onDestinationSelected: (String) -> Unit
) {
    if (isRadiantUi) {
        RadiantBottomNavBar(backdrop, selectedRoute, onDestinationSelected)
    } else {
        ClassicBottomNavBar(backdrop, selectedRoute, onDestinationSelected)
    }
}

@Composable
private fun BoxScope.RadiantBottomNavBar(
    backdrop: Backdrop,
    selectedRoute: String?,
    onDestinationSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val guidePreferences = remember {
        context.getSharedPreferences("app_guide", Context.MODE_PRIVATE)
    }
    var tabGuideShown by remember {
        mutableStateOf(guidePreferences.getBoolean("xxt_tab_guide_shown", false))
    }
    var tabsBounds by remember { mutableStateOf<Rect?>(null) }
    fun dismissGuide() {
        if (!tabGuideShown) {
            tabGuideShown = true
            guidePreferences.edit().putBoolean("xxt_tab_guide_shown", true).apply()
        }
    }

    val showingSchedule = XuexiaotongDockState.tab == XuexiaotongSubTab.SCHEDULE
    val destinations = listOf(
        RadiantDestination("home", "主页", R.drawable.ic_nav_home),
        RadiantDestination("schedule", "课表", R.drawable.ic_nav_schedule),
        RadiantDestination(
            "xuexiaotong",
            if (showingSchedule) "日程" else "课程",
            if (showingSchedule) R.drawable.ic_nav_plan else R.drawable.ic_nav_degree_hat
        ),
        RadiantDestination("settings", "设置", R.drawable.ic_nav_settings)
    )
    if (selectedRoute !in destinations.map { it.route }) return

    fun select(route: String) {
        if (route == "xuexiaotong" && route == selectedRoute) {
            dismissGuide()
            XuexiaotongDockState.toggle()
        } else {
            onDestinationSelected(route)
        }
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
                onTabSelected = { onDestinationSelected(destinations[it].route) },
                onCurrentTabTapped = { selectedRoute?.let(::select) },
                backdrop = backdrop,
                tabsCount = destinations.size,
                modifier = Modifier
                    .padding(horizontal = 36.dp)
                    .onGloballyPositioned { tabsBounds = it.boundsInWindow() }
            ) {
                destinations.forEach { destination ->
                    val selected = selectedRoute == destination.route
                    LiquidBottomTab(
                        selected = selected,
                        onClick = { select(destination.route) }
                    ) {
                        Icon(
                            painter = painterResource(destination.iconId),
                            contentDescription = destination.label
                        )
                        Text(destination.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    } else {
        MaterialNavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { tabsBounds = it.boundsInWindow() },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            destinations.forEach { destination ->
                val selected = selectedRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { select(destination.route) },
                    icon = {
                        Icon(
                            painter = painterResource(destination.iconId),
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    colors = appNavigationBarItemColors()
                )
            }
        }
    }

    if (!tabGuideShown && selectedRoute == "xuexiaotong") {
        tabsBounds?.let { bounds ->
            var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
            var guideVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(350)
                guideVisible = true
                // 6 秒后自动消失并记录已展示，避免气泡长期悬浮
                delay(6000)
                dismissGuide()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopStart)
                    .onGloballyPositioned { overlayOrigin = it.boundsInWindow().topLeft }
            ) {
                AnimatedVisibility(
                    visible = guideVisible,
                    enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 3 }
                ) {
                    AnchoredGuideBubble(
                        anchorCenterX = { bounds.left + bounds.width * 0.625f - overlayOrigin.x },
                        anchorTopY = { bounds.top - overlayOrigin.y },
                        backdrop = backdrop,
                        text = "再次点击可切换日程 / 课程页"
                    )
                }
            }
        }
    }
}

@Composable
private fun AnchoredGuideBubble(
    anchorCenterX: () -> Float,
    anchorTopY: () -> Float,
    backdrop: Backdrop,
    text: String,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            GuideBubbleCard(text = text, backdrop = backdrop)
        },
        modifier = modifier
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )
        val margin = 10.dp.roundToPx()
        val parentWidth = constraints.maxWidth
        val anchorX = anchorCenterX().roundToInt()
        val x = (anchorX - placeable.width / 2)
            .coerceIn(margin, (parentWidth - placeable.width - margin).coerceAtLeast(margin))
        val y = anchorTopY().roundToInt() - placeable.height - 10.dp.roundToPx()
        layout(parentWidth, constraints.maxHeight) {
            placeable.placeRelative(x, y)
        }
    }
}

@Composable
private fun GuideBubbleCard(
    text: String,
    backdrop: Backdrop
) {
    val glassContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.64f)
    val infiniteTransition = rememberInfiniteTransition(label = "guideBubble")
    val bubbleBob by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubbleBob"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // 纯展示气泡：不带任何 pointer 修饰符，触摸事件完全穿透到下层内容，
        // 收起依赖 6s 超时或切换子页时触发
        modifier = Modifier
            .graphicsLayer { translationY = bubbleBob * 3.dp.toPx() }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousCapsule },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                highlight = { Highlight.Default },
                shadow = { Shadow() },
                onDrawSurface = { drawRect(glassContainerColor) }
            )
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BoxScope.ClassicBottomNavBar(
    backdrop: Backdrop,
    selectedRoute: String?,
    onDestinationSelected: (String) -> Unit
) {
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
                onTabSelected = { onDestinationSelected(classicDestinations[it].route) },
                backdrop = backdrop,
                tabsCount = classicDestinations.size,
                modifier = Modifier.padding(horizontal = 36.dp)
            ) {
                classicDestinations.forEach { destination ->
                    val selected = selectedRoute == destination.route
                    LiquidBottomTab(
                        selected = selected,
                        onClick = { onDestinationSelected(destination.route) }
                    ) {
                        Icon(
                            imageVector = if (selected) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                            contentDescription = destination.label
                        )
                        Text(destination.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    } else if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        val selectedIndex = classicDestinations
            .indexOfFirst { it.route == selectedRoute }
            .coerceAtLeast(0)
        MiuixNavigationBar(
            items = classicDestinations.mapIndexed { index, destination ->
                MiuixNavigationItem(
                    label = destination.label,
                    icon = if (index == selectedIndex) {
                        destination.selectedIcon
                    } else {
                        destination.unselectedIcon
                    }
                )
            },
            selected = selectedIndex,
            onClick = { onDestinationSelected(classicDestinations[it].route) },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
    } else {
        MaterialNavigationBar(
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
                    onClick = { onDestinationSelected(destination.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    colors = appNavigationBarItemColors()
                )
            }
        }
    }
}

@Composable
private fun appNavigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)
