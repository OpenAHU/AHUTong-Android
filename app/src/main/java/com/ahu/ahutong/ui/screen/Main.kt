package com.ahu.ahutong.ui.screen

import android.graphics.BitmapFactory
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.BuildConfig
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
import com.ahu.ahutong.ui.screen.main.BathroomDeposit
import com.ahu.ahutong.ui.screen.main.Billing
import com.ahu.ahutong.ui.screen.main.BillingStats
import com.ahu.ahutong.ui.screen.main.CardBalanceDeposit
import com.ahu.ahutong.ui.screen.main.ElectricityDeposit
import com.ahu.ahutong.ui.screen.main.ElectricityAlertSettings
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
import com.ahu.ahutong.ui.screen.main.RepositorySettings
import com.ahu.ahutong.ui.screen.main.Weather
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongScreen
import com.ahu.ahutong.ui.screen.settings.Contributors
import com.ahu.ahutong.ui.screen.settings.Debug
import com.ahu.ahutong.ui.screen.settings.PrivacyPolicyScreen
import com.ahu.ahutong.ui.screen.settings.License
import com.ahu.ahutong.R
import androidx.compose.ui.res.stringResource
import com.ahu.ahutong.ui.screen.settings.Preferences
import com.ahu.ahutong.ui.screen.settings.ThemeLab
import com.ahu.ahutong.ui.screen.setup.Info
import com.ahu.ahutong.ui.screen.setup.Login
import com.ahu.ahutong.ui.components.LiquidGlassAppHost
import com.ahu.ahutong.ui.components.AppBackground
import com.ahu.ahutong.ui.components.LocalAppBackground
import com.ahu.ahutong.ui.components.LocalGlassEffectsReduced
import com.ahu.ahutong.ui.components.LocalGlassReadabilityBoost
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.ui.components.LocalLiquidGlassAmbientBackdrop
import com.ahu.ahutong.ui.components.LocalLiquidGlassContentBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppDialogSurface
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.captureLiquidGlassContent
import com.ahu.ahutong.ui.state.AboutViewModel
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.ui.state.ElectricityDepositViewModel
import com.ahu.ahutong.ui.state.ElectricityAlertViewModel
import com.ahu.ahutong.ui.state.LoginViewModel
import com.ahu.ahutong.ui.state.MainViewModel
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.ahu.ahutong.utils.animatedComposable
import com.ahu.ahutong.utils.NavigationObservationPolicy
import com.ahu.ahutong.utils.NavigationSnapshot
import com.kyant.monet.n1
import com.kyant.monet.withNight
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.diagnostics.DiagnosticsContribution
import com.ahu.ahutong.personalization.prefetch.PaymentQrOpenCommandStore
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.recorder.BehaviorRecorder
import com.ahu.ahutong.ui.suggestion.SmartSuggestionHost
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.core.storage.HomeBackgroundStore
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

private val primaryDestinationRoutes = listOf("home", "schedule", "xuexiaotong", "settings")

internal fun settingsParentRoute(uiTheme: AppUiTheme): String =
    if (uiTheme == AppUiTheme.RADIANT) "settings" else "home"

internal fun shouldNormalizePreferencesParent(
    uiTheme: AppUiTheme,
    previousRoute: String?
): Boolean = previousRoute != settingsParentRoute(uiTheme)

@OptIn(ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun Main(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel(),
    loginViewModel: LoginViewModel = viewModel(),
    postgraduateScheduleViewModel: com.ahu.ahutong.ui.state.PostgraduateScheduleViewModel = viewModel(),
    discoveryViewModel: DiscoveryViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = hiltViewModel(),
    aboutViewModel: AboutViewModel = viewModel(),
    behaviorRuntime: BehaviorPredictionRuntime,
    behaviorRecorder: BehaviorRecorder,
    diagnosticsContribution: DiagnosticsContribution,
    paymentQrCommands: PaymentQrOpenCommandStore,
    isReLoginShown: Boolean,
    onReLoginDismiss: () -> Unit
) {
    val electricityAlertViewModel: ElectricityAlertViewModel = hiltViewModel()
    val electricityRechargeSelection by electricityAlertViewModel.rechargeSelection.collectAsState()
    val academicType by com.ahu.ahutong.data.dao.AHUCache.academicTypeUpdates().collectAsState()
    val undergraduateEnabled = academicType != com.ahu.ahutong.data.model.AcademicAccountType.POSTGRADUATE ||
        com.ahu.ahutong.data.dao.AHUCache.getMockData()
    val visiblePrimaryRoutes = remember(undergraduateEnabled) {
        if (undergraduateEnabled) primaryDestinationRoutes
        else primaryDestinationRoutes.filterNot { it == "xuexiaotong" }
    }
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
    val effectiveRoute = currentRoute
    // 合并注：IA 已收敛为 Radiant 单形态（无主屏 pager），isHomeActive 简化为路由判断
    val isHomeActive = currentRoute == "home"

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
        if (!AHUCache.canOpenRoute(route)) return
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
    }

    fun requestHomeEdit() {
        behaviorRuntime.recordActionIntentAsync(
            AppActionId.EDIT_HOME,
            ActionSource.ORGANIC
        )
        shouldEnterHomeEdit = true
        scope.launch { selectPrimaryDestination("home") }
    }

    fun navigateBackFromPreferences() {
        if (!navController.popBackStack("settings", inclusive = false)) {
            navController.navigate("settings") {
                popUpTo("home") { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(currentRoute, appUiTheme) {
        if (
            currentRoute == "preferences" &&
            shouldNormalizePreferencesParent(
                appUiTheme,
                navController.previousBackStackEntry?.destination?.route
            )
        ) {
            navController.navigate("settings") {
                popUpTo("home") { inclusive = false }
            }
            navController.navigate("preferences") { launchSingleTop = true }
        }
    }

    val navigationSnapshot = NavigationSnapshot(
        route = effectiveRoute,
        entryId = currentBackStackEntry?.id,
        previousEntryId = navController.previousBackStackEntry?.id,
        uiTheme = appUiTheme,
        diagnostics = diagnosticsRouteVisible
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

    val backgroundRevision by HomeBackgroundStore.revision.collectAsState()
    val appBackground = remember(backgroundRevision) {
        if (HomeBackgroundStore.isEnabled) {
            HomeBackgroundStore.blurredFile(context).takeIf { it.exists() }
                ?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
                ?.let { AppBackground(it, HomeBackgroundStore.maskPercent / 100f) }
        } else null
    }
    CompositionLocalProvider(
        LocalAppBackground provides appBackground,
        LocalGlassReadabilityBoost provides (appBackground != null),
        // 低端机性能开关：revision 变化（含开关切换）时同步重组
        LocalGlassEffectsReduced provides HomeBackgroundStore.reduceGlassEffects
    ) {
    LiquidGlassAppHost(modifier = Modifier.fillMaxSize()) {
        val contentBackdrop = LocalLiquidGlassContentBackdrop.current
        // 自定义背景在 ambient 层、页面内容在 content 层（两层分离）——
        // 导航栏模糊源只给 content 层会漏掉背景图，胶囊在图上几乎透明（P2 回归）。
        // 有背景时给导航栏「ambient + content」组合采样层，恢复对背景的模糊。
        val navBackdrop = if (appBackground != null) {
            rememberCombinedBackdrop(LocalLiquidGlassAmbientBackdrop.current, contentBackdrop)
        } else {
            contentBackdrop
        }
        val backdrop = navBackdrop
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier
                .captureLiquidGlassContent()
                .fillMaxSize()
                .appLiquidGlassSceneBackground(96.n1 withNight 10.n1)
        ) {
            animatedComposable("home") {
                Home(
                    discoveryViewModel = discoveryViewModel,
                    scheduleViewModel = scheduleViewModel,
                    navController = navController,
                    behaviorRuntime = behaviorRuntime,
                    isActive = isHomeActive,
                    onOpenSchedule = {
                        scope.launch { selectPrimaryDestination("schedule") }
                    },
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
                        if (undergraduateEnabled) {
                            scheduleViewModel.loadConfig()
                            scheduleViewModel.refreshSchedule()
                        }
                        if (undergraduateEnabled) scope.launch {
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
                            navController.navigate("home") {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                            com.ahu.ahutong.data.session.SessionStore.currentUser()?.xh?.takeIf { it.isNotBlank() }?.let {
                                behaviorRuntime.startProfile(it)
                            }
                            homeEditGrayState = GrayReleaseManager.state(
                                GrayFeatures.HomeEdit,
                                context
                            )
                        }
                        discoveryViewModel.loadActivityBean()
                        if (AHUCache.canUseUndergraduateAcademics()) {
                            scheduleViewModel.loadConfig()
                            scheduleViewModel.refreshSchedule()
                        }
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
                // 无条件渲染，理由同 settings 路由：redirect 的二次 pop 会与
                // 返回转场竞态；非 RADIANT 主题下独立展示课表页可正常用系统返回
                Schedule(
                    scheduleViewModel = scheduleViewModel,
                    behaviorRecorder = behaviorRecorder,
                    graduateController = if (undergraduateEnabled) null else postgraduateScheduleViewModel,
                    graduateAccountId = if (undergraduateEnabled) null else SessionStore.currentUser()?.xh
                )
            }
            animatedComposable("tools") {
                // IA 统一后小工具页只存在于「主页-更多」二级路由；旧 tools 深链一律转 widgets
                LaunchedEffect(Unit) {
                    navController.navigate("widgets") {
                        popUpTo("tools") { inclusive = true }
                        launchSingleTop = true
                    }
                }
                Box(modifier = Modifier.fillMaxSize())
            }
            animatedComposable("widgets") {
                MoreWidgetsScreen(
                    navController = navController,
                    homeEditEnabled = homeEditGrayState.enabled,
                    onEditHome = ::requestHomeEdit
                )
            }
            animatedComposable("school_calendar") {
                SchoolCalendar(navController = navController)
            }
            animatedComposable("grade") {
                Grade(
                    onNavigateToEvaluation = {
                        navController.navigate("evaluation")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            animatedComposable("phone_book") {
                PhoneBook(onBack = { navController.popBackStack() })
            }
            animatedComposable("exam") {
                Exam(onBack = { navController.popBackStack() })
            }
            animatedComposable("evaluation") {
                Evaluation(onBack = { navController.popBackStack() })
            }
            animatedComposable("free_classroom") {
                FreeClassroom(onBack = { navController.popBackStack() })
            }
            animatedComposable("lost_found") {
                LostFound(onBack = { navController.popBackStack() })
            }
            animatedComposable("identity_code") {
                // 身份码：菜鸟驿站（淘宝内页）。tbopen scheme 优先拉起淘宝 App
                // （参考 IdentityCodeTool 工程验证），未装淘宝再回退浏览器；即发即退不落页。
                val h5Url = "https://pages-fast.m.taobao.com/wow/z/uniapp/1011717/last-mile-fe/end-collect-platform/identity-code"
                LaunchedEffect(Unit) {
                    val tbScheme = "tbopen://m.taobao.com/tbopen/index.html" +
                        "?action=ali.open.nav&module=h5&bootImage=0&h5Url=" +
                        java.net.URLEncoder.encode(h5Url, "UTF-8")
                    val opened = runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(tbScheme)
                            )
                        )
                    }.isSuccess
                    if (!opened) {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(h5Url)
                                )
                            )
                        }
                    }
                    navController.popBackStack()
                }
            }
            animatedComposable("weather") {
                Weather(onBack = { navController.popBackStack() })
            }
            animatedComposable(REPOSITORY_ROUTE) {
                Repository(
                    navController = navController,
                    path = "",
                    behaviorRecorder = behaviorRecorder
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
                    behaviorRecorder = behaviorRecorder
                )
            }
            animatedComposable("repository_downloads") {
                RepositoryDownloads(navController = navController)
            }
            animatedComposable("repository_settings") {
                RepositorySettings(navController = navController)
            }
            animatedComposable("settings") {
                // 无条件渲染：切换主题后 pop 回此路由时不能落入空白重定向页，
                // 否则 pop 转场与 redirect 的二次 pop 竞态会导致白屏/卡死
                SettingsHub(
                    navController = navController,
                    mainViewModel = mainViewModel,
                    scheduleViewModel = scheduleViewModel
                )
            }
            animatedComposable("settings__privacy_policy") {
                val res = LocalContext.current.resources
                val policyMarkdown by produceState<String?>(null) {
                    value = withContext(Dispatchers.IO) {
                        res.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
                    }
                }
                policyMarkdown?.let {
                    PrivacyPolicyScreen(markdown = it, onBack = { navController.popBackStack() })
                }
            }
            animatedComposable("settings__license") {
                License(
                    title = stringResource(R.string.license),
                    onBack = { navController.popBackStack() }
                )
            }
            animatedComposable("settings__contributors") {
                Contributors(
                    title = stringResource(R.string.contributors),
                    onBack = { navController.popBackStack() }
                )
            }

            animatedComposable("preferences") {
                Preferences(
                    onBack = ::navigateBackFromPreferences,
                    onOpenThemeLab = { navController.navigate("settings__theme_lab") },
                    undergraduateEnabled = undergraduateEnabled
                )
            }

            animatedComposable("settings__theme_lab") {
                ThemeLab(onBack = { navController.popBackStack() })
            }

            animatedComposable("electricity_pay") {
                ElectricityDeposit(
                    onBack = { navController.popBackStack() },
                    onOpenRecentRooms = { navController.navigate("electricity_recent_rooms") },
                    onOpenAlertSettings = { navController.navigate("electricity_alert_settings") },
                    initialSelection = electricityRechargeSelection,
                    onInitialSelectionConsumed = electricityAlertViewModel::selectionConsumed
                )
            }

            animatedComposable("electricity_alert_settings") {
                // Scope the selector to this entry; configuring alerts must not change the pay page.
                val settingsViewModel: ElectricityDepositViewModel = hiltViewModel()
                ElectricityAlertSettings(
                    onBack = { navController.popBackStack() },
                    viewModel = settingsViewModel
                )
            }

            animatedComposable("electricity_recent_rooms") { backStackEntry ->
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

            animatedComposable("card_balance_deposit") {
                CardBalanceDeposit(navController = navController)
            }

            animatedComposable("billing") {
                Billing(
                    onBack = { navController.popBackStack() },
                    onOpenStats = { navController.navigate("billing_stats") }
                )
            }

            animatedComposable("billing_stats") {
                BillingStats(onBack = { navController.popBackStack() })
            }

            animatedComposable("bathroom_deposit") {
                BathroomDeposit(onBack = { navController.popBackStack() })
            }

            animatedComposable("cmb_card_recharge") {
                CardBalanceDeposit(navController = navController)
            }

            animatedComposable("network_recharge") {
                NetworkRecharge(onBack = { navController.popBackStack() })
            }

            animatedComposable("xuexiaotong") {
                XuexiaotongScreen()
            }

            if (BuildConfig.DEBUG) {
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
            }

            animatedComposable("splash") {
                Splash(navController)
            }
            diagnosticsContribution.installRoutes(this, navController, behaviorRuntime)
        }
        BottomNavBar(
            backdrop = backdrop,
            selectedRoute = currentRoute,
            onDestinationSelected = { route ->
                scope.launch { selectPrimaryDestination(route) }
            }
        )
        val productUiBlocked = effectiveRoute == "login" || effectiveRoute == "setup" ||
            effectiveRoute == "splash" || effectiveRoute?.contains("deposit") == true ||
            effectiveRoute?.contains("recharge") == true ||
            effectiveRoute in setOf("electricity_pay", "electricity_recent_rooms", "electricity_alert_settings") ||
            isReLoginShown || suggestionOverlayBlocked || imeVisible
        SmartSuggestionHost(
            runtime = behaviorRuntime,
            backdrop = backdrop,
            blocked = productUiBlocked,
            hiddenForDiagnostics = diagnosticsRouteVisible,
            bottomSpacing = if (effectiveRoute in visiblePrimaryRoutes) {
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
                            selectPrimaryDestination(route, ActionSource.SUGGESTION)
                        }
                    }
                }
            },
            modifier = Modifier
        )
        with(diagnosticsContribution) {
            Overlay(navController, behaviorRuntime, productUiBlocked)
        }
        ElectricityAlertHost(
            navController = navController,
            route = currentRoute,
            loginState = loginViewModel.state,
            paymentQrCommands = paymentQrCommands,
            viewModel = electricityAlertViewModel
        )
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
}
