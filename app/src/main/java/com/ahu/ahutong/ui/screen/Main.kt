package com.ahu.ahutong.ui.screen

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ahu.ahutong.appwidget.ScheduleAppWidgetReceiver
import com.ahu.ahutong.data.gray.GrayFeatures
import com.ahu.ahutong.data.gray.GrayReleaseManager
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.screen.main.BathroomDeposit
import com.ahu.ahutong.ui.screen.main.CardBalanceDeposit
import com.ahu.ahutong.ui.screen.main.CmbCardRecharge
import com.ahu.ahutong.ui.screen.main.ElectricityDeposit
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
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.AboutViewModel
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.ui.state.LoginViewModel
import com.ahu.ahutong.ui.state.MainViewModel
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.ahu.ahutong.utils.animatedComposable
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.launch
import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.diagnostics.DiagnosticsContribution
import com.ahu.ahutong.personalization.prefetch.PaymentQrOpenCommandStore
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.ui.SmartSuggestionHost
import com.ahu.ahutong.personalization.action.AppActionId

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
    var firstDestination by remember { mutableStateOf(true) }
    var lastBackStackDepth by remember { mutableIntStateOf(0) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val currentBackStack by navController.currentBackStack.collectAsState()
    val currentBackStackDepth = currentBackStack.size
    val suggestionOverlayBlocked by behaviorRuntime.suggestionOverlayBlocked.collectAsState()
    val imeVisible = WindowInsets.isImeVisible
    val diagnosticsRouteVisible = diagnosticsContribution.isDiagnosticsRoute(currentRoute) ||
        currentRoute == "debug"

    LaunchedEffect(currentRoute, currentBackStackDepth) {
        val route = currentRoute ?: return@LaunchedEffect
        val isBackStackRestore = lastBackStackDepth > 0 && currentBackStackDepth < lastBackStackDepth
        behaviorRuntime.onRouteChanged(
            route,
            when {
                diagnosticsRouteVisible -> ActionSource.DEBUG
                firstDestination || isBackStackRestore -> ActionSource.RESTORE
                else -> ActionSource.ORGANIC
            }
        )
        firstDestination = false
        lastBackStackDepth = currentBackStackDepth
    }

    LaunchedEffect(Unit) {
        homeEditGrayState = GrayReleaseManager.state(GrayFeatures.HomeEdit, context)
    }

    Box {
        val backdrop = rememberLayerBackdrop()
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier
                .layerBackdrop(backdrop)
                .fillMaxSize()
                .background(96.n1 withNight 10.n1)
        ) {
            animatedComposable("home") {
                Home(
                    discoveryViewModel = discoveryViewModel,
                    scheduleViewModel = scheduleViewModel,
                    navController = navController,
                    behaviorRuntime = behaviorRuntime,
                    homeEditEnabled = homeEditGrayState.enabled,
                    enterEditModeRequest = shouldEnterHomeEdit,
                    onEnterEditModeRequestConsumed = {
                        shouldEnterHomeEdit = false
                    }
                )
            }
            animatedComposable("setup") {
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
            animatedComposable("login") {
                Login(
                    loginViewModel = loginViewModel,
                    onLoggedIn = {
                        scheduleViewModel.clear()
                        scope.launch {
                            com.ahu.ahutong.data.dao.AHUCache.getCurrentUser()?.xh?.takeIf { it.isNotBlank() }?.let {
                                behaviorRuntime.startProfile(it)
                            }
                            homeEditGrayState = GrayReleaseManager.state(
                                GrayFeatures.HomeEdit,
                                context
                            )
                        }
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                        discoveryViewModel.loadActivityBean()
                        scheduleViewModel.loadConfig()
                        scheduleViewModel.refreshSchedule()
                    }
                )
            }
            animatedComposable("info") {
                Info(
                    scheduleViewModel = scheduleViewModel,
                    onSetup = { navController.popBackStack() }
                )
            }
            animatedComposable("schedule") {
                Schedule(scheduleViewModel = scheduleViewModel, behaviorRuntime = behaviorRuntime)
            }
            animatedComposable("tools") {
                Tools(
                    navController = navController,
                    homeEditEnabled = homeEditGrayState.enabled,
                    onEditHome = {
                        behaviorRuntime.recordActionIntentAsync(AppActionId.EDIT_HOME, ActionSource.ORGANIC)
                        shouldEnterHomeEdit = true
                    }
                )
            }
            animatedComposable("widgets") {
                MoreWidgetsScreen(
                    navController = navController,
                    homeEditEnabled = homeEditGrayState.enabled,
                    onEditHome = {
                        behaviorRuntime.recordActionIntentAsync(AppActionId.EDIT_HOME, ActionSource.ORGANIC)
                        shouldEnterHomeEdit = true
                    }
                )
            }
            animatedComposable("school_calendar") {
                SchoolCalendar(navController = navController)
            }
            animatedComposable("grade") {
                Grade(
                    onNavigateToEvaluation = {
                        navController.navigate("evaluation")
                    }
                )
            }
            animatedComposable("phone_book") {
                PhoneBook()
            }
            animatedComposable("exam") {
                Exam()
            }
            animatedComposable("evaluation") {
                Evaluation()
            }
            animatedComposable("free_classroom") {
                FreeClassroom()
            }
            animatedComposable("lost_found") {
                LostFound()
            }
            animatedComposable("weather") {
                Weather()
            }
            animatedComposable(REPOSITORY_ROUTE) {
                Repository(
                    navController = navController,
                    path = "",
                    behaviorRuntime = behaviorRuntime
                )
            }
            animatedComposable(
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
            animatedComposable("repository_downloads") {
                RepositoryDownloads(navController = navController)
            }
            animatedComposable("repository_settings") {
                RepositorySettings(navController = navController)
            }
            animatedComposable("settings") {
                Settings(
                    navController = navController,
                    mainViewModel = mainViewModel,
                    aboutViewModel = aboutViewModel,
                    behaviorRuntime = behaviorRuntime
                )
            }
            animatedComposable("settings__license") {
                License()
            }
            animatedComposable("settings__contributors") {
                Contributors()
            }

            animatedComposable("preferences") {
                Preferences(onBack = { navController.popBackStack() })
            }

            animatedComposable("electricity_pay") {
                ElectricityDeposit()
            }

            animatedComposable("card_balance_deposit") {
                CardBalanceDeposit(navController = navController)
            }

            animatedComposable("bathroom_deposit") {
                BathroomDeposit()
            }

            animatedComposable("cmb_card_recharge") {
                CmbCardRecharge(
                    onExit = { navController.popBackStack() },
                    onRechargeSuccessExit = {
                        val returnedHome = navController.popBackStack("home", inclusive = false)
                        if (!returnedHome) {
                            navController.navigate("home") {
                                popUpTo("cmb_card_recharge") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            animatedComposable("network_recharge") {
                NetworkRecharge()
            }

            animatedComposable("xuexiaotong") {
                val context = LocalContext.current
                val api = remember { com.ahu.ahutong.data.xuexiaotong.ChaoxingApi(context) }
                XuexiaotongScreen(api = api)
            }

            animatedComposable("debug") {
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

            animatedComposable("splash") {
                Splash(navController)
            }
            diagnosticsContribution.installRoutes(this, navController, behaviorRuntime)
        }
        BottomNavBar(navController, backdrop)
        val productUiBlocked = currentRoute == "login" || currentRoute == "setup" ||
            currentRoute == "splash" || currentRoute?.contains("deposit") == true ||
            currentRoute?.contains("recharge") == true || currentRoute == "electricity_pay" ||
            isReLoginShown || suggestionOverlayBlocked || imeVisible
        SmartSuggestionHost(
            runtime = behaviorRuntime,
            backdrop = backdrop,
            blocked = productUiBlocked,
            hiddenForDiagnostics = diagnosticsRouteVisible,
            bottomSpacing = when {
                currentRoute in setOf("home", "schedule", "settings") -> 88.dp
                isRadiantUi && currentRoute == "xuexiaotong" -> 88.dp
                !isRadiantUi && currentRoute == "tools" -> 88.dp
                else -> 16.dp
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
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    }
                }
            },
            modifier = Modifier
        )
        with(diagnosticsContribution) {
            Overlay(navController, behaviorRuntime, productUiBlocked)
        }
    }
    if (isReLoginShown) {
        Dialog(
            onDismissRequest = { onReLoginDismiss() },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Column(
                modifier = Modifier
                    .clip(SmoothRoundedCornerShape(32.dp))
                    .background(96.n1 withNight 10.n1)
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "当前登录状态已过期，请重新登录!",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = 0.n1 withNight 100.n1,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "重新登录",
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(ContinuousCapsule)
                        .background(90.a1 withNight 30.n1)
                        .clickable {
                            navController.navigate("login")
                            onReLoginDismiss()
                        }
                        .padding(12.dp, 8.dp),
                    color = 100.n1 withNight 100.n1,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
