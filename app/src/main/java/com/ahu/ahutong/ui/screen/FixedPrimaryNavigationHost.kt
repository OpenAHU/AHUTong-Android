package com.ahu.ahutong.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ahu.ahutong.utils.SecondaryPageEnterFadeMillis
import com.ahu.ahutong.utils.SecondaryPageExitFadeMillis
import com.ahu.ahutong.utils.LocalSecondaryPageVisibility
import com.ahu.ahutong.utils.SecondaryPageVisibility
import com.kyant.backdrop.Backdrop

private val LocalPrimaryNavigationAlphas =
    staticCompositionLocalOf<MutableMap<String, State<Float>>?> { null }

/** Fixed chrome waits for incoming content, then follows its opacity without horizontal movement. */
@Composable
internal fun FixedPrimaryNavigationHost(
    navController: NavHostController,
    primaryRoutes: List<String>,
    backdrop: Backdrop,
    materialFloatingBottomBar: Boolean,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val entries by navController.visibleEntries.collectAsState()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val visiblePrimaryEntries = entries.filter { it.destination.route in primaryRoutes }
    val currentPrimary = currentRoute?.takeIf { it in primaryRoutes }
    val outgoingPrimary = visiblePrimaryEntries.lastOrNull()
    var lastPrimaryRoute by remember { mutableStateOf<String?>(null) }
    var primaryExitTarget by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentEntry?.id, currentPrimary, outgoingPrimary?.id) {
        when {
            currentPrimary != null -> {
                lastPrimaryRoute = currentPrimary
                primaryExitTarget = null
            }
            outgoingPrimary != null -> {
                lastPrimaryRoute = outgoingPrimary.destination.route
                primaryExitTarget = currentEntry?.id
            }
            primaryExitTarget != currentEntry?.id -> primaryExitTarget = null
        }
    }
    val primaryExiting = currentPrimary == null && (outgoingPrimary != null ||
        (primaryExitTarget != null && primaryExitTarget == currentEntry?.id))
    val selectedRoute = currentPrimary ?: outgoingPrimary?.destination?.route
        ?: lastPrimaryRoute.takeIf { primaryExiting }
    val alphas = remember { mutableStateMapOf<String, State<Float>>() }
    val secondaryPages = remember { mutableStateMapOf<String, SecondaryPageVisibility>() }
    val incoming = secondaryPages[currentEntry?.id]
    val primaryToPrimary = currentPrimary != null && entries.none {
        it.destination !is NavGraph && it.destination.route !in primaryRoutes && it.destination.route != "splash"
    }
    val alpha = when {
        !enabled -> 0f
        primaryToPrimary -> 1f
        primaryExiting -> when {
            incoming != null -> if (incoming.laidOut.value) 1f - incoming.alpha.value else 1f
            outgoingPrimary != null -> 1f
            else -> 0f
        }
        else -> visiblePrimaryEntries.maxOfOrNull { alphas[it.id]?.value ?: 0f } ?: 0f
    }
    val interactive = enabled && currentPrimary != null

    CompositionLocalProvider(LocalPrimaryNavigationAlphas provides alphas,
        LocalSecondaryPageVisibility provides secondaryPages) {
        Box(modifier) {
            content()
            if (selectedRoute != null && alpha > 0f) {
                Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }.then(
                    if (interactive) Modifier else Modifier.clearAndSetSemantics { }
                )) {
                    BottomNavBar(
                        backdrop = backdrop,
                        selectedRoute = selectedRoute,
                        materialFloatingBottomBar = materialFloatingBottomBar,
                        onDestinationSelected = { route ->
                            // A stale accessibility or touch action cannot navigate away from a child.
                            if (enabled && navController.currentBackStackEntry?.destination?.route in primaryRoutes) {
                                onDestinationSelected(route)
                            }
                        }
                    )
                }
            }
        }
    }
}

/** A primary destination reports its actual NavHost transition; it owns no navigation chrome. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun AnimatedVisibilityScope.PrimaryNavigationDestination(
    entry: NavBackStackEntry,
    content: @Composable () -> Unit
) {
    val alphas = LocalPrimaryNavigationAlphas.current
    val alpha = transition.animateFloat(
        transitionSpec = {
            tween(if (targetState == EnterExitState.Visible) SecondaryPageEnterFadeMillis else SecondaryPageExitFadeMillis)
        },
        label = "primaryNavigationOpacity"
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    DisposableEffect(alphas, entry.id, alpha) {
        alphas?.set(entry.id, alpha)
        onDispose { alphas?.remove(entry.id) }
    }
    content()
}
