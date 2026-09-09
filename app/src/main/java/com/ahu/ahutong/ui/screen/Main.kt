package com.ahu.ahutong.ui.screen

import com.ahu.ahutong.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ahu.ahutong.appwidget.ScheduleAppWidgetReceiver
import com.ahu.ahutong.data.gray.GrayFeatures
import com.ahu.ahutong.data.gray.GrayReleaseManager
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.screen.main.BathroomDeposit
import com.ahu.ahutong.ui.screen.main.CardBalanceDeposit
import com.ahu.ahutong.ui.screen.main.ElectricityDeposit
import com.ahu.ahutong.ui.screen.main.ElectricityRecentRooms
import com.ahu.ahutong.ui.screen.main.Evaluation
import com.ahu.ahutong.ui.screen.main.Exam
import com.ahu.ahutong.ui.screen.main.FreeClassroom
import com.ahu.ahutong.ui.screen.main.Grade
import com.ahu.ahutong.ui.screen.main.Home
import com.ahu.ahutong.ui.screen.main.LostFound
import com.ahu.ahutong.ui.screen.main.MoreWidgetsScreen
import com.ahu.ahutong.ui.screen.main.NetworkRecharge
import com.ahu.ahutong.ui.screen.main.PhoneBook
import com.ahu.ahutong.ui.screen.main.Repository
import com.ahu.ahutong.ui.screen.main.RepositoryDownloads
import com.ahu.ahutong.ui.screen.main.Schedule
import com.ahu.ahutong.ui.screen.main.REPOSITORY_DIRECTORY_ROUTE
import com.ahu.ahutong.ui.screen.main.REPOSITORY_PATH_ARG
import com.ahu.ahutong.ui.screen.main.REPOSITORY_ROUTE
import com.ahu.ahutong.ui.screen.main.SchoolCalendar
import com.ahu.ahutong.ui.screen.main.Tools
import com.ahu.ahutong.ui.screen.main.RepositorySettings
import com.ahu.ahutong.ui.screen.main.Weather
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongScreen
import com.ahu.ahutong.ui.screen.settings.Contributors
import com.ahu.ahutong.ui.screen.settings.Debug
import com.ahu.ahutong.ui.screen.settings.License
import com.ahu.ahutong.ui.screen.settings.Preferences
import com.ahu.ahutong.ui.screen.setup.Info
import com.ahu.ahutong.ui.screen.setup.Login
import com.ahu.ahutong.ui.components.LiquidGlassAppHost
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.ui.components.LocalLiquidGlassContentBackdrop
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppDialogSurface
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.captureLiquidGlassContent
import com.ahu.ahutong.ui.state.AboutViewModel
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.ui.state.ElectricityDepositViewModel
import com.ahu.ahutong.ui.state.LoginViewModel
import com.ahu.ahutong.ui.state.MainViewModel
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.ahu.ahutong.utils.animatedComposable
import com.ahu.ahutong.utils.NavigationObservationPolicy
import com.ahu.ahutong.utils.NavigationSnapshot
import com.ahu.ahutong.utils.resolveVisibleRoute
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.diagnostics.DiagnosticsContribution
import com.ahu.ahutong.personalization.prefetch.PaymentQrOpenCommandStore
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.ui.SmartSuggestionHost
import com.ahu.ahutong.personalization.action.AppActionId

private val primaryDestinationRoutes = listOf("home", "schedule", "tools", "settings")

@OptIn(ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun Main(
    navController: NavHostController,
    mainViewModel: MainViewModel = viewModel(),
    loginViewModel: LoginViewModel = viewModel(),
    discoveryViewModel: DiscoveryViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = viewModel(),
    aboutViewModel: AboutViewModel = viewModel(),
    behaviorRuntime: BehaviorPredictionRuntime,
    diagnosticsContribution: DiagnosticsContribution,
    paymentQrCommands: PaymentQrOpenCommandStore,
    isReLoginShown: Boolean,
    onReLoginDismiss: () -> Unit
) {
    var shouldEnterHomeEdit by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var homeEditGrayState by remember {
        mutableStateOf(GrayReleaseManager.localState(GrayFeatures.HomeEdit, context))
    }
    val navigationPolicy = remember { NavigationObservationPolicy() }
    var navigationObservationRevision by remember { mutableIntStateOf(0) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val suggestionOverlayBlocked by behaviorRuntime.suggestionOverlayBlocked.collectAsState()
    val imeVisible = WindowInsets.isImeVisible
    val diagnosticsRouteVisible = diagnosticsContribution.isDiagnosticsRoute(currentRoute) ||
        currentRoute == "debug"
    val appUiTheme = LocalAppUiTheme.current
    val appUiThemeState = rememberUpdatedState(appUiTheme)
    val primaryPagerState = rememberPagerState(pageCount = { primaryDestinationRoutes.size })
    var preloadPrimaryNeighbors by remember { mutableStateOf(false) }
    val primaryRoute = primaryDestinationRoutes[primaryPagerState.currentPage]
    val effectiveRoute = resolveVisibleRoute(
        currentRoute,
        appUiTheme,
        primaryDestinationRoutes[primaryPagerState.settledPage]
    )

    fun cancelSelection(token: Long) {
        if (navigationPolicy.cancelSelection(token)) {
            // Cancellation can leave the same already-settled snapshot on screen. Re-observe it
            // now that the pending target no longer blocks it, even without another Pager change.
            navigationObservationRevision++
        }
    }

    suspend fun selectPrimaryDestination(
        route: String,
        source: ActionSource = ActionSource.ORGANIC
    ) {
        if (appUiTheme == AppUiTheme.RADIANT) {
            val target = if (route == "tools") "widgets" else route
            if (target == navController.currentBackStackEntry?.destination?.route) return
            val selectionToken = navigationPolicy.expectSelection(target, source)
            try {
                navController.navigate(target) {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                }
            } catch (error: Exception) {
                cancelSelection(selectionToken)
                throw error
            }
            return
        }
        val destinationIndex = primaryDestinationRoutes.indexOf(route)
        if (destinationIndex < 0) return
        if (destinationIndex == primaryPagerState.settledPage &&
            !primaryPagerState.isScrollInProgress
        ) return
        val selectionToken = navigationPolicy.expectSelection(route, source)
        try {
            primaryPagerState.animateScrollToPage(
                page = destinationIndex,
                animationSpec = tween(durationMillis = 260)
            )
        } catch (error: Exception) {
            cancelSelection(selectionToken)
            throw error
        }
    }

    fun requestHomeEdit() {
        behaviorRuntime.recordActionIntentAsync(
            AppActionId.EDIT_HOME,
            ActionSource.ORGANIC
        )
        shouldEnterHomeEdit = true
        scope.launch { selectPrimaryDestination("home") }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == "home") {
            delay(1_500L)
            preloadPrimaryNeighbors = true
        }
    }

    LaunchedEffect(appUiTheme) {
        if (appUiTheme != AppUiTheme.RADIANT && currentRoute == "xuexiaotong") {
            navController.navigate("home") {
                popUpTo("home") { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    val navigationSnapshot = NavigationSnapshot(
        route = effectiveRoute,
        entryId = currentBackStackEntry?.id,
        previousEntryId = navController.previousBackStackEntry?.id,
        uiTheme = appUiTheme,
        settled = appUiTheme == AppUiTheme.RADIANT || currentRoute != "home" ||
            !primaryPagerState.isScrollInProgress,
        diagnostics = diagnosticsRouteVisible,
        primaryPagerHost = appUiTheme != AppUiTheme.RADIANT && currentRoute == "home"
    )
    LaunchedEffect(navigationSnapshot, navigationObservationRevision) {
        val observation = navigationPolicy.observe(navigationSnapshot) ?: return@LaunchedEffect
        if (observation.synchronizeOnly) {
            // A theme may change the visible page in the same entry. Keep prediction context
            // accurate without inventing a navigation action for the theme preference itself.
            behaviorRuntime.suppressNextRoute(observation.route)
        }
        behaviorRuntime.onRouteChanged(
            observation.route,
            observation.source
        )
    }

    LaunchedEffect(Unit) {
        homeEditGrayState = GrayReleaseManager.state(GrayFeatures.HomeEdit, context)
    }

    LiquidGlassAppHost(modifier = Modifier.fillMaxSize()) {
        val backdrop = LocalLiquidGlassContentBackdrop.current
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier
                .captureLiquidGlassContent()
                .fillMaxSize()
                .appLiquidGlassSceneBackground(96.n1 withNight 10.n1)
        ) {
            animatedComposable(appUiThemeState, "home") {
                if (appUiTheme == AppUiTheme.RADIANT) {
                    Home(
                        discoveryViewModel = discoveryViewModel,
                        scheduleViewModel = scheduleViewModel,
                        navController = navController,
                        behaviorRuntime = behaviorRuntime,
                        onOpenSchedule = {
                            scope.launch { selectPrimaryDestination("schedule") }
                        },
                        homeEditEnabled = homeEditGrayState.enabled,
                        enterEditModeRequest = shouldEnterHomeEdit,
                        onEnterEditModeRequestConsumed = {
                            shouldEnterHomeEdit = false
                        }
                    )
                } else {
                    // Register before the pages so their own modal/edit BackHandlers take priority.
                    BackHandler(
                        enabled = currentRoute == "home" &&
                            (primaryPagerState.settledPage != 0 ||
                                primaryPagerState.currentPage != 0 ||
                                primaryPagerState.targetPage != 0)
                    ) {
                        scope.launch { selectPrimaryDestination("home", ActionSource.RESTORE) }
                    }
                    HorizontalPager(
                        state = primaryPagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = if (preloadPrimaryNeighbors) 1 else 0,
                        userScrollEnabled = false,
                        key = primaryDestinationRoutes::get
                    ) { page ->
                        when (page) {
                            0 -> Home(
                                discoveryViewModel = discoveryViewModel,
                                scheduleViewModel = scheduleViewModel,
                                navController = navController,
                                behaviorRuntime = behaviorRuntime,
                                onOpenSchedule = {
                                    scope.launch { selectPrimaryDestination("schedule") }
                                },
                                homeEditEnabled = homeEditGrayState.enabled,
                                enterEditModeRequest = shouldEnterHomeEdit,
                                onEnterEditModeRequestConsumed = {
                                    shouldEnterHomeEdit = false
                                }
                            )
                            1 -> Schedule(
                                scheduleViewModel = scheduleViewModel,
                                behaviorRuntime = behaviorRuntime,
                                isActive = primaryPagerState.settledPage == 1
                            )
                            2 -> Tools(
                                navController = navController,
                                homeEditEnabled = homeEditGrayState.enabled,
                                onEditHome = ::requestHomeEdit
                            )
                            3 -> Settings(
                                navController = navController,
                                mainViewModel = mainViewModel,
                                aboutViewModel = aboutViewModel,
                                scheduleViewModel = scheduleViewModel,
                                behaviorRuntime = behaviorRuntime
                            )
                        }
                    }
                }
            }
            animatedComposable(appUiThemeState, "setup") {
                Setup(
                    scheduleViewModel = scheduleViewModel,
                    aboutViewModel = aboutViewModel,
                    onSetup = {
                        navController.popBackStack()
                        discoveryViewModel.loadActivityBean()
                        scheduleViewModel.loadConfig()
                        scheduleViewModel.refreshSchedule()
                        scope.launch {
                            GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                                ScheduleAppWidgetReceiver::class.java
                            )
                        }
                    }
                )
            }
            animatedComposable(appUiThemeState, "login") {
                Login(
                    loginViewModel = loginViewModel,
                    onLoggedIn = {
                        scheduleViewModel.clear()
                        scope.launch {
                            navController.navigate("home") {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                            primaryPagerState.scrollToPage(0)
                            com.ahu.ahutong.data.dao.AHUCache.getCurrentUser()?.xh?.takeIf { it.isNotBlank() }?.let {
                                behaviorRuntime.startProfile(it)
                            }
                            homeEditGrayState = GrayReleaseManager.state(
                                GrayFeatures.HomeEdit,
                                context
                            )
                        }
                        discoveryViewModel.loadActivityBean()
                        scheduleViewModel.loadConfig()
                        scheduleViewModel.refreshSchedule()
                    }
                )
            }
            animatedComposable(appUiThemeState, "info") {
                Info(
                    scheduleViewModel = scheduleViewModel,
                    onSetup = { navController.popBackStack() }
                )
            }
            animatedComposable(appUiThemeState, "schedule") {
                // 无条件渲染，理由同 settings 路由：redirect 的二次 pop 会与
                // 返回转场竞态；非 RADIANT 主题下独立展示课表页可正常用系统返回
                Schedule(
                    scheduleViewModel = scheduleViewModel,
                    behaviorRuntime = behaviorRuntime
                )
            }
            animatedComposable(appUiThemeState, "tools") {
                if (appUiTheme == AppUiTheme.RADIANT) {
                    LaunchedEffect(Unit) {
                        navController.navigate("widgets") {
                            popUpTo("tools") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    PrimaryDestinationRedirect(
                        navController = navController,
                        onRedirect = { primaryPagerState.scrollToPage(2) }
                    )
                }
            }
            animatedComposable(appUiThemeState, "widgets") {
                MoreWidgetsScreen(
                    navController = navController,
                    homeEditEnabled = homeEditGrayState.enabled,
                    onEditHome = ::requestHomeEdit
                )
            }
            animatedComposable(appUiThemeState, "school_calendar") {
                SchoolCalendar(navController = navController)
            }
            animatedComposable(appUiThemeState, "grade") {
                Grade(
                    onNavigateToEvaluation = {
                        navController.navigate("evaluation")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            animatedComposable(appUiThemeState, "phone_book") {
                PhoneBook(onBack = { navController.popBackStack() })
            }
            animatedComposable(appUiThemeState, "exam") {
                Exam(onBack = { navController.popBackStack() })
            }
            animatedComposable(appUiThemeState, "evaluation") {
                Evaluation(onBack = { navController.popBackStack() })
            }
            animatedComposable(appUiThemeState, "free_classroom") {
                FreeClassroom(onBack = { navController.popBackStack() })
            }
            animatedComposable(appUiThemeState, "lost_found") {
                LostFound(onBack = { navController.popBackStack() })
            }
            animatedComposable(appUiThemeState, "weather") {
                Weather(onBack = { navController.popBackStack() })
            }
            animatedComposable(appUiThemeState, REPOSITORY_ROUTE) {
                Repository(
                    navController = navController,
                    path = "",
                    behaviorRuntime = behaviorRuntime
                )
            }
            animatedComposable(
                appUiThemeState,
                route = REPOSITORY_DIRECTORY_ROUTE,
                arguments = listOf(
                    navArgument(REPOSITORY_PATH_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                Repository(
                    navController = navController,
                    path = backStackEntry.arguments?.getString(REPOSITORY_PATH_ARG).orEmpty(),
                    behaviorRuntime = behaviorRuntime
                )
            }
            animatedComposable(appUiThemeState, "repository_downloads") {
                RepositoryDownloads(navController = navController)
            }
            animatedComposable(appUiThemeState, "repository_settings") {
                RepositorySettings(navController = navController)
            }
            animatedComposable(appUiThemeState, "settings") {
                // 无条件渲染：切换主题后 pop 回此路由时不能落入空白重定向页，
                // 否则 pop 转场与 redirect 的二次 pop 竞态会导致白屏/卡死
                Settings(
                    navController = navController,
                    mainViewModel = mainViewModel,
                    aboutViewModel = aboutViewModel,
                    scheduleViewModel = scheduleViewModel,
                    behaviorRuntime = behaviorRuntime
                )
            }
            animatedComposable(appUiThemeState, "settings__license") {
                License(onBack = { navController.popBackStack() })
            }
            animatedComposable(appUiThemeState, "settings__contributors") {
                Contributors(onBack = { navController.popBackStack() })
            }

            animatedComposable(appUiThemeState, "preferences") {
                Preferences(onBack = { navController.popBackStack() })
            }

            animatedComposable(appUiThemeState, "electricity_pay") {
                ElectricityDeposit(
                    onBack = { navController.popBackStack() },
                    onOpenRecentRooms = { navController.navigate("electricity_recent_rooms") }
                )
            }

            animatedComposable(appUiThemeState, "electricity_recent_rooms") { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("electricity_pay")
                }
                val electricityViewModel: ElectricityDepositViewModel = hiltViewModel(parentEntry)
                ElectricityRecentRooms(
                    onBack = { navController.popBackStack() },
                    onRoomSelected = { navController.popBackStack() },
                    viewModel = electricityViewModel
                )
            }

            animatedComposable(appUiThemeState, "card_balance_deposit") {
                CardBalanceDeposit(navController = navController)
            }

            animatedComposable(appUiThemeState, "bathroom_deposit") {
                BathroomDeposit(onBack = { navController.popBackStack() })
            }

            animatedComposable(appUiThemeState, "cmb_card_recharge") {
                CardBalanceDeposit(navController = navController)
            }

            animatedComposable(appUiThemeState, "network_recharge") {
                NetworkRecharge(onBack = { navController.popBackStack() })
            }

            animatedComposable(appUiThemeState, "xuexiaotong") {
                val api = remember { com.ahu.ahutong.data.xuexiaotong.ChaoxingApi(context) }
                XuexiaotongScreen(api = api)
            }

            if (BuildConfig.DEBUG) {
                animatedComposable(appUiThemeState, "debug") {
                    Debug(
                        scheduleViewModel = scheduleViewModel,
                        discoveryViewModel = discoveryViewModel,
                        onGrayStateChanged = {
                            scope.launch {
                                homeEditGrayState = GrayReleaseManager.state(
                                    GrayFeatures.HomeEdit,
                                    context
                                )
                            }
                        }
                    )
                }
            }

            animatedComposable(appUiThemeState, "splash") {
                Splash(navController)
            }
            diagnosticsContribution.installRoutes(this, navController, behaviorRuntime)
        }
        BottomNavBar(
            backdrop = backdrop,
            selectedRoute = when {
                appUiTheme == AppUiTheme.RADIANT -> currentRoute
                currentRoute == "home" -> primaryRoute
                else -> null
            },
            onDestinationSelected = { route ->
                if (appUiTheme == AppUiTheme.RADIANT) {
                    scope.launch { selectPrimaryDestination(route) }
                } else if (route == "xuexiaotong") {
                    navController.navigate(route) { launchSingleTop = true }
                } else {
                    scope.launch {
                        selectPrimaryDestination(route)
                        if (currentRoute != "home") {
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
        )
        val productUiBlocked = effectiveRoute == "login" || effectiveRoute == "setup" ||
            effectiveRoute == "splash" || effectiveRoute?.contains("deposit") == true ||
            effectiveRoute?.contains("recharge") == true ||
            effectiveRoute in setOf("electricity_pay", "electricity_recent_rooms") ||
            isReLoginShown || suggestionOverlayBlocked || imeVisible
        SmartSuggestionHost(
            runtime = behaviorRuntime,
            backdrop = backdrop,
            blocked = productUiBlocked,
            hiddenForDiagnostics = diagnosticsRouteVisible,
            bottomSpacing = if (
                effectiveRoute in primaryDestinationRoutes ||
                appUiTheme == AppUiTheme.RADIANT && currentRoute == "xuexiaotong"
            ) {
                88.dp
            } else {
                16.dp
            },
            onSuggestionClick = { suggestion ->
                scope.launch {
                    val action = behaviorRuntime.acceptSuggestion(suggestion.executionId) ?: return@launch
                    if (action == AppActionId.OPEN_PAYMENT_QR) {
                        paymentQrCommands.publish(
                            suggestion.executionId,
                            suggestion.decisionId,
                            ActionSource.SUGGESTION
                        )
                        behaviorRuntime.suppressNextRoute("home")
                        navController.navigate("home") { launchSingleTop = true }
                    } else {
                        com.ahu.ahutong.personalization.action.AppActionCatalog.spec(action).route?.let { route ->
                            if (appUiTheme == AppUiTheme.RADIANT) {
                                selectPrimaryDestination(route, ActionSource.SUGGESTION)
                            } else if (route in primaryDestinationRoutes) {
                                if (currentRoute != "home") {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                                selectPrimaryDestination(route, ActionSource.SUGGESTION)
                            } else {
                                navController.navigate(route) { launchSingleTop = true }
                            }
                        }
                    }
                }
            },
            modifier = Modifier
        )
        with(diagnosticsContribution) {
            Overlay(navController, behaviorRuntime, productUiBlocked)
        }
        if (isReLoginShown) {
            AppDialogSurface(
                onDismissRequest = { onReLoginDismiss() },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "当前登录状态已过期，请重新登录!",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )
                    AppButton(
                        onClick = {
                            navController.navigate("login")
                            onReLoginDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("重新登录", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryDestinationRedirect(
    navController: NavHostController,
    onRedirect: suspend () -> Unit
) {
    LaunchedEffect(Unit) {
        onRedirect()
        if (!navController.popBackStack("home", inclusive = false)) {
            navController.navigate("home") {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize())
}
