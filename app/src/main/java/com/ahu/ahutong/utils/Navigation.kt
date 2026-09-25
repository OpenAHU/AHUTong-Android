package com.ahu.ahutong.utils

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** 一级页面顺序（与 Main.kt 的 primaryDestinationRoutes 保持一致，转场方向按此推导）。 */
private val primaryDestinationOrder = listOf("home", "schedule", "xuexiaotong", "settings")

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

/**
 * 全主题统一转场（解耦收敛：不再按主题分叉）。
 * 一级页面之间：方向感知全幅横向滑动；其余（次级页进出）：淡化 + 小偏移滑动。
 */
@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
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
            if (isPrimaryDestinationTransition(
                    initialState.destination.route,
                    targetState.destination.route
                )
            ) {
                slideInHorizontally(
                    initialOffsetX = { direction * it },
                    animationSpec = tween(220)
                )
            } else {
                fadeIn(animationSpec = tween(180)) +
                    slideInHorizontally(
                        initialOffsetX = { direction * it / 4 },
                        animationSpec = tween(240)
                    )
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
            if (isPrimaryDestinationTransition(
                    initialState.destination.route,
                    targetState.destination.route
                )
            ) {
                slideOutHorizontally(
                    targetOffsetX = { -direction * it },
                    animationSpec = tween(220)
                )
            } else {
                fadeOut(animationSpec = tween(140)) +
                    slideOutHorizontally(
                        targetOffsetX = { -direction * it / 12 },
                        animationSpec = tween(220)
                    )
            }
        }
    },
    popEnterTransition = {
        val direction = horizontalDirection(
            targetState.destination.route,
            initialState.destination.route
        )
        if (isPrimaryDestinationTransition(
                targetState.destination.route,
                initialState.destination.route
            )
        ) {
            slideInHorizontally(
                initialOffsetX = { -direction * it },
                animationSpec = tween(220)
            )
        } else {
            fadeIn(animationSpec = tween(180)) +
                slideInHorizontally(
                    initialOffsetX = { -direction * it / 12 },
                    animationSpec = tween(260)
                )
        }
    },
    popExitTransition = {
        val direction = horizontalDirection(
            targetState.destination.route,
            initialState.destination.route
        )
        if (isPrimaryDestinationTransition(
                targetState.destination.route,
                initialState.destination.route
            )
        ) {
            slideOutHorizontally(
                targetOffsetX = { direction * it },
                animationSpec = tween(220)
            )
        } else {
            fadeOut(animationSpec = tween(160)) +
                slideOutHorizontally(
                    targetOffsetX = { direction * it / 4 },
                    animationSpec = tween(260)
                )
        }
    },
    content = { entry ->
        if (com.ahu.ahutong.data.dao.AHUCache.canOpenRoute(route)) {
            content(entry)
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Text("研究生账号暂不支持本科教务功能")
            }
        }
    }
)
