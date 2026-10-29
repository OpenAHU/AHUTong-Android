package com.ahu.ahutong.utils

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** 一级页面顺序（与 Main.kt 的 primaryDestinationRoutes 保持一致，转场方向按此推导）。 */
private val primaryDestinationOrder = listOf("home", "schedule", "xuexiaotong", "settings")

internal const val SecondaryPageEnterFadeMillis = 180
internal const val SecondaryPageExitFadeMillis = 140

internal class SecondaryPageVisibility(val alpha: State<Float>, val laidOut: State<Boolean>)

internal val LocalSecondaryPageVisibility =
    staticCompositionLocalOf<MutableMap<String, SecondaryPageVisibility>?> { null }

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedVisibilityScope.SecondaryPageContent(
    entry: NavBackStackEntry,
    content: @Composable () -> Unit
) {
    val pages = LocalSecondaryPageVisibility.current
    if (pages == null) {
        content()
        return
    }
    val laidOut = remember(entry.id) { mutableStateOf(false) }
    val alpha = transition.animateFloat(
        transitionSpec = { tween(SecondaryPageEnterFadeMillis) }, label = "secondaryPageVisibility"
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val visibility = remember(alpha, laidOut) { SecondaryPageVisibility(alpha, laidOut) }
    DisposableEffect(pages, entry.id, visibility) {
        pages[entry.id] = visibility
        onDispose { pages.remove(entry.id) }
    }
    // Read the content's size, not NavHost's forced full-screen minimum constraints.
    Box(Modifier.wrapContentSize(Alignment.TopStart).onSizeChanged { size ->
        if (size.width > 0 && size.height > 0) laidOut.value = true
    }) { content() }
}

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
                fadeIn(animationSpec = tween(SecondaryPageEnterFadeMillis)) +
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
                fadeOut(animationSpec = tween(SecondaryPageExitFadeMillis)) +
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
            fadeIn(animationSpec = tween(SecondaryPageEnterFadeMillis)) +
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
        val pageContent: @Composable () -> Unit = {
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
        if (route in primaryDestinationOrder) pageContent()
        else SecondaryPageContent(entry, pageContent)
    }
)
