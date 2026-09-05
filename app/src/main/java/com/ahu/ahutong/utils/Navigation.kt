package com.ahu.ahutong.utils

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ahu.ahutong.data.model.AppUiTheme

private val primaryDestinationOrder = listOf("home", "schedule", "tools", "settings")

private fun isPrimaryDestinationTransition(fromRoute: String?, toRoute: String?): Boolean =
    fromRoute in primaryDestinationOrder && toRoute in primaryDestinationOrder

private fun horizontalDirection(fromRoute: String?, toRoute: String?): Int {
    val fromIndex = primaryDestinationOrder.indexOf(fromRoute)
    val toIndex = primaryDestinationOrder.indexOf(toRoute)
    return if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
        if (toIndex > fromIndex) 1 else -1
    } else {
        1
    }
}

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) = animatedComposable(
    uiTheme = AppUiTheme.MATERIAL,
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    content = content
)

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.animatedComposable(
    uiTheme: AppUiTheme,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) = animatedComposableWithThemeProvider(
    uiTheme = { uiTheme },
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    content = content
)

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.animatedComposable(
    uiTheme: State<AppUiTheme>,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) = animatedComposableWithThemeProvider(
    uiTheme = { uiTheme.value },
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    content = content
)

@OptIn(ExperimentalAnimationApi::class)
private fun NavGraphBuilder.animatedComposableWithThemeProvider(
    uiTheme: () -> AppUiTheme,
    route: String,
    arguments: List<NamedNavArgument>,
    deepLinks: List<NavDeepLink>,
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    enterTransition = {
        if (initialState.destination.route == "splash") {
            EnterTransition.None
        } else {
            val direction = horizontalDirection(
                initialState.destination.route,
                targetState.destination.route
            )
            if (uiTheme() != AppUiTheme.RADIANT &&
                isPrimaryDestinationTransition(
                    initialState.destination.route,
                    targetState.destination.route
                )
            ) {
                slideInHorizontally(
                    initialOffsetX = { direction * it },
                    animationSpec = tween(220)
                )
            } else {
                when (uiTheme()) {
                    // 对称交叉淡化：新页淡入与旧页淡出时长一致且无延迟，
                    // 任意时刻两者不透明度之和恒为 1，避免中段亮度塌陷
                    // 视觉等效"渐入播两次"的闪断；旧页不缩放，防闪断处尺寸跳动
                    AppUiTheme.RADIANT ->
                        fadeIn(animationSpec = tween(180)) +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(180))
                    AppUiTheme.MATERIAL ->
                        fadeIn(animationSpec = tween(160)) +
                            slideInHorizontally(
                                initialOffsetX = { direction * it / 4 },
                                animationSpec = tween(240)
                            )
                    AppUiTheme.MIUIX ->
                        fadeIn(animationSpec = tween(180)) +
                            slideInHorizontally(
                                initialOffsetX = { direction * it / 5 },
                                animationSpec = tween(280)
                            )
                    AppUiTheme.LIQUID_GLASS ->
                        fadeIn(animationSpec = tween(160)) +
                            slideInHorizontally(
                                initialOffsetX = { direction * it / 4 },
                                animationSpec = tween(260)
                            )
                }
            }
        }
    },
    exitTransition = {
        if (targetState.destination.route == "home" &&
            initialState.destination.route == "splash"
        ) {
            ExitTransition.None
        } else {
            val direction = horizontalDirection(
                initialState.destination.route,
                targetState.destination.route
            )
            if (uiTheme() != AppUiTheme.RADIANT &&
                isPrimaryDestinationTransition(
                    initialState.destination.route,
                    targetState.destination.route
                )
            ) {
                slideOutHorizontally(
                    targetOffsetX = { -direction * it },
                    animationSpec = tween(220)
                )
            } else {
                when (uiTheme()) {
                    AppUiTheme.RADIANT -> fadeOut(animationSpec = tween(180))
                    AppUiTheme.MATERIAL, AppUiTheme.MIUIX, AppUiTheme.LIQUID_GLASS ->
                        fadeOut(animationSpec = tween(140)) +
                            slideOutHorizontally(
                                targetOffsetX = { -direction * it / 12 },
                                animationSpec = tween(220)
                            )
                }
            }
        }
    },
    popEnterTransition = {
        val direction = horizontalDirection(
            targetState.destination.route,
            initialState.destination.route
        )
        if (uiTheme() != AppUiTheme.RADIANT &&
            isPrimaryDestinationTransition(
                targetState.destination.route,
                initialState.destination.route
            )
        ) {
            slideInHorizontally(
                initialOffsetX = { -direction * it },
                animationSpec = tween(220)
            )
        } else {
            when (uiTheme()) {
                AppUiTheme.RADIANT ->
                    fadeIn(animationSpec = tween(180)) +
                        scaleIn(initialScale = 0.96f, animationSpec = tween(180))
                AppUiTheme.MATERIAL, AppUiTheme.MIUIX, AppUiTheme.LIQUID_GLASS ->
                    fadeIn(animationSpec = tween(180)) +
                        slideInHorizontally(
                            initialOffsetX = { -direction * it / 12 },
                            animationSpec = tween(260)
                        )
            }
        }
    },
    popExitTransition = {
        val direction = horizontalDirection(
            targetState.destination.route,
            initialState.destination.route
        )
        if (uiTheme() != AppUiTheme.RADIANT &&
            isPrimaryDestinationTransition(
                targetState.destination.route,
                initialState.destination.route
            )
        ) {
            slideOutHorizontally(
                targetOffsetX = { direction * it },
                animationSpec = tween(220)
            )
        } else {
            when (uiTheme()) {
                AppUiTheme.RADIANT -> fadeOut(animationSpec = tween(180))
                AppUiTheme.MATERIAL, AppUiTheme.MIUIX, AppUiTheme.LIQUID_GLASS ->
                    fadeOut(animationSpec = tween(160)) +
                        slideOutHorizontally(
                            targetOffsetX = { direction * it / 4 },
                            animationSpec = tween(260)
                        )
            }
        }
    },
    content = content
)
