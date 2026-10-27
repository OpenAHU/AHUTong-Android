package com.ahu.ahutong.ui.screen

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import com.ahu.ahutong.restartMainAfterLogin
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.ahu.ahutong.ui.screen.scan.QrRouter
import com.ahu.ahutong.ui.screen.scan.ScanOverlay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.ahu.ahutong.ui.screen.canteen.CanteenFootprintScreen
import com.ahu.ahutong.ui.screen.canteen.CanteenRankingScreen
import com.ahu.ahutong.ui.screen.attendance.AttendanceScreen
import com.ahu.ahutong.ui.screen.main.BillingStats
import com.ahu.ahutong.ui.screen.main.CardBalanceDeposit
import com.ahu.ahutong.ui.screen.main.CampusNoticeScreen
import com.ahu.ahutong.ui.screen.main.ElectricityDeposit
import com.ahu.ahutong.ui.screen.main.ElectricityAlertSettings
import com.ahu.ahutong.ui.screen.main.ElectricityRecentRooms
import com.ahu.ahutong.ui.screen.main.Evaluation
import com.ahu.ahutong.ui.screen.main.Exam
import com.ahu.ahutong.ui.screen.main.FreeClassroom
import com.ahu.ahutong.ui.screen.main.ProgramCompletion
import com.ahu.ahutong.ui.screen.main.Grade
import com.ahu.ahutong.ui.screen.main.Home
import com.ahu.ahutong.ui.screen.main.LostFound
import com.ahu.ahutong.ui.screen.main.MoreWidgetsScreen
import com.ahu.ahutong.ui.screen.main.NetworkRecharge
import com.ahu.ahutong.ui.screen.main.PhoneBook
import com.ahu.ahutong.ui.screen.main.StudentMailScreen
import com.ahu.ahutong.ui.screen.main.Repository
import com.ahu.ahutong.ui.screen.main.RepositoryDownloads
import com.ahu.ahutong.ui.screen.main.Schedule
import com.ahu.ahutong.ui.screen.main.schedule.CustomCoursesScreen
import com.ahu.ahutong.ui.screen.main.REPOSITORY_DIRECTORY_ROUTE
import com.ahu.ahutong.ui.screen.main.REPOSITORY_PATH_ARG
import com.ahu.ahutong.ui.screen.main.REPOSITORY_ROUTE
import com.ahu.ahutong.ui.screen.main.SchoolCalendar
import com.ahu.ahutong.ui.screen.main.RepositorySettings
import com.ahu.ahutong.ui.screen.main.Weather
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongScreen
import com.ahu.ahutong.ui.screen.profile.ProfileScreen
import com.ahu.ahutong.ui.screen.settings.Contributors
import com.ahu.ahutong.ui.screen.settings.EarlyAccessScreen
import com.ahu.ahutong.ui.screen.settings.EarlyAccessInvitationHost
import com.ahu.ahutong.data.preview.EarlyAccessManager
import com.ahu.ahutong.ui.screen.settings.Debug
import com.ahu.ahutong.ui.screen.settings.PrivacyPolicyScreen
import com.ahu.ahutong.ui.screen.settings.License
import com.ahu.ahutong.R
import androidx.compose.ui.res.stringResource
import com.ahu.ahutong.ui.screen.settings.Preferences
import com.ahu.ahutong.ui.screen.settings.ThemeLab
import com.ahu.ahutong.ui.screen.setup.Info
import com.ahu.ahutong.ui.screen.setup.Login
import com.ahu.ahutong.ui.screen.setup.canCancelLogin
import com.ahu.ahutong.ui.screen.setup.canUseAuthenticatedPages
import com.ahu.ahutong.ui.screen.setup.LoginNavigationGate
import com.ahu.ahutong.ui.components.LiquidGlassAppHost
import com.ahu.ahutong.ui.components.AppBackground
import com.ahu.ahutong.ui.components.LocalAppBackground
import com.ahu.ahutong.ui.components.LocalGlassEffectsReduced
import com.ahu.ahutong.ui.components.LocalGlassReadabilityBoost
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.ui.components.AppPageHeader
import com.ahu.ahutong.ui.components.LocalLiquidGlassAmbientBackdrop
import com.ahu.ahutong.ui.components.LocalLiquidGlassContentBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.captureLiquidGlassContent
import com.ahu.ahutong.ui.state.AboutViewModel
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.ui.state.ElectricityDepositViewModel
import com.ahu.ahutong.ui.state.ElectricityAlertViewModel
import com.ahu.ahutong.ui.state.LoginViewModel
import com.ahu.ahutong.ui.state.MainViewModel
import com.ahu.ahutong.ui.component.UpdateStatusStrip
import com.ahu.ahutong.ui.component.UpdateStatusStripHeight
import com.ahu.ahutong.ui.component.UpdateStripPhase
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
import com.ahu.ahutong.data.session.AhuSession
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.core.storage.HomeBackgroundStore
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

private val primaryDestinationRoutes = listOf("home", "schedule", "xuexiaotong", "settings")

internal fun settingsParentRoute(uiTheme: AppUiTheme): String =
    // Every theme, including legacy LiquidGlass, now has a real settings destination.
    when (uiTheme) {
        AppUiTheme.MATERIAL, AppUiTheme.MIUIX, AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> "settings"
    }

internal fun shouldNormalizePreferencesParent(
    uiTheme: AppUiTheme,
    previousRoute: String?
): Boolean = previousRoute != settingsParentRoute(uiTheme)

@Composable
internal fun NormalizePreferencesParent(
    navController: NavHostController,
    currentRoute: String?,
    uiTheme: AppUiTheme
) {
    LaunchedEffect(currentRoute, uiTheme) {
        if (
            currentRoute == "preferences" &&
            shouldNormalizePreferencesParent(
                uiTheme,
                navController.previousBackStackEntry?.destination?.route
            )
        ) {
            navController.navigate("settings") {
                popUpTo("home") { inclusive = false }
            }
            navController.navigate("preferences") { launchSingleTop = true }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun Main(
    navController: NavHostController,
    session: AhuSession,
    mainViewModel: MainViewModel = hiltViewModel(),
    loginViewModel: LoginViewModel = viewModel(),
    postgraduateScheduleViewModel: com.ahu.ahutong.ui.state.PostgraduateScheduleViewModel = viewModel(),
    discoveryViewModel: DiscoveryViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = hiltViewModel(),
    aboutViewModel: AboutViewModel = viewModel(),
    behaviorRuntime: BehaviorPredictionRuntime,
    behaviorRecorder: BehaviorRecorder,
    diagnosticsContribution: DiagnosticsContribution,
    paymentQrCommands: PaymentQrOpenCommandStore
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
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var homeEditGrayState by remember {
        mutableStateOf(GrayReleaseManager.localState(GrayFeatures.HomeEdit, context))
    }
    val navigationPolicy = remember { NavigationObservationPolicy() }
    var navigationObservationRevision by remember { mutableIntStateOf(0) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val sessionStatus by session.state.collectAsState()
    val earlyAccessState by EarlyAccessManager.state.collectAsState()
    LaunchedEffect(earlyAccessState.subject, earlyAccessState.joined) {
        mainViewModel.onUpdateChannelChanged()
    }
    val authenticationRequired by LoginNavigationGate.authenticationRequired.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var sessionCheckRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) sessionCheckRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val canUseBusinessPages = remember(sessionStatus, authenticationRequired, loginViewModel.state, sessionCheckRevision, currentRoute) {
        canUseAuthenticatedPages(SessionStore.isLoggedIn(), sessionStatus, authenticationRequired)
    }
    val protectedRouteBlocked = currentRoute != null &&
        currentRoute !in setOf("login", "splash") && !canUseBusinessPages
    LaunchedEffect(protectedRouteBlocked, currentRoute, sessionStatus) {
        if (!protectedRouteBlocked) return@LaunchedEffect
        // Refresh and confirmation finish before the session publishes Expired.
        // A queued effect must not navigate using a stale composition snapshot.
        if (canUseAuthenticatedPages(
                SessionStore.isLoggedIn(), session.state.value,
                LoginNavigationGate.authenticationRequired.value
            )) return@LaunchedEffect
        navController.navigate(if (SessionStore.isUserRestoreUnavailable()) "splash" else "login") {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
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
        if (!canUseAuthenticatedPages(
                SessionStore.isLoggedIn(), AhuSessionState.status.value,
                LoginNavigationGate.authenticationRequired.value
            ) ||
            !AHUCache.canOpenRoute(route)
        ) return
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

    NormalizePreferencesParent(navController, currentRoute, appUiTheme)

    val navigationSnapshot = NavigationSnapshot(
        route = effectiveRoute,
        entryId = currentBackStackEntry?.id,
        previousEntryId = navController.previousBackStackEntry?.id,
        uiTheme = appUiTheme,
        diagnostics = diagnosticsRouteVisible
    )
    LaunchedEffect(navigationSnapshot, navigationObservationRevision) {
        val observation = navigationPolicy.observe(navigationSnapshot) ?: return@LaunchedEffect
        com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics.recordRuntime(
            "切换页面 ${observation.route.substringBefore('?')}"
        )
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

    val scanShortcutEnabled by mainViewModel.scanShortcutEnabled.collectAsState()
    // 一键扫码浮层（同页弹窗，不跳导航页）
    var scanOpen by remember { mutableStateOf(false) }
    val backgroundRevision by HomeBackgroundStore.revision.collectAsState()
    val backgroundImageRevision by HomeBackgroundStore.imageRevision.collectAsState()
    val backgroundBitmap by produceState<ImageBitmap?>(null, context, backgroundImageRevision) {
        value = withContext(Dispatchers.IO) {
            if (HomeBackgroundStore.isEnabled) {
                HomeBackgroundStore.blurredFile(context).takeIf { it.exists() }
                    ?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
            } else null
        }
    }
    val appBackground = remember(backgroundBitmap, backgroundRevision) {
        backgroundBitmap?.let { AppBackground(it, HomeBackgroundStore.maskPercent / 100f) }
    }
    CompositionLocalProvider(
        LocalAppBackground provides appBackground,
        LocalGlassReadabilityBoost provides (appBackground != null),
        // 低端机性能开关：revision 变化（含开关切换）时同步重组
        LocalGlassEffectsReduced provides HomeBackgroundStore.reduceGlassEffects
    ) {
    LiquidGlassAppHost(modifier = Modifier.fillMaxSize()) {
        val contentBackdrop = LocalLiquidGlassContentBackdrop.current
        // 插件注册表初始化 + 列表状态（装/卸插件自动重组路由与小工具页）
        val plugins by com.ahu.ahutong.ui.plugin.PluginRegistry.plugins.collectAsState()
        LaunchedEffect(Unit) { com.ahu.ahutong.ui.plugin.PluginRegistry.init(context) }
        // 自定义背景在 ambient 层、页面内容在 content 层（两层分离）——
        // 导航栏模糊源只给 content 层会漏掉背景图，胶囊在图上几乎透明（P2 回归）。
        // 有背景时给导航栏「ambient + content」组合采样层，恢复对背景的模糊。
        val navBackdrop = if (appBackground != null) {
            rememberCombinedBackdrop(LocalLiquidGlassAmbientBackdrop.current, contentBackdrop)
        } else {
            contentBackdrop
        }
        val backdrop = navBackdrop
        val updateInfo = mainViewModel.apkUpdateInfo.value
        val primaryPageVisible = !protectedRouteBlocked && currentRoute in visiblePrimaryRoutes
        val showUpdateStrip = primaryPageVisible && updateInfo != null &&
            mainViewModel.apkUpdateStripDismissedVersion.value != updateInfo.versionCode
        val updateStripProgress = remember { Animatable(0f) }
        LaunchedEffect(showUpdateStrip) {
            updateStripProgress.animateTo(
                targetValue = if (showUpdateStrip) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (showUpdateStrip) 380 else 300,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
        val updateInset = UpdateStatusStripHeight * updateStripProgress.value
        Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(bottom = updateInset)) {
        if (protectedRouteBlocked) {
            com.ahu.ahutong.ui.screen.setup.Splash()
        } else {
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
                    undergraduateEnabled = undergraduateEnabled,
                    accountId = SessionStore.currentUser()?.xh,
                    onOpenSchedule = {
                        scope.launch { selectPrimaryDestination("schedule") }
                    },
                    homeEditEnabled = homeEditGrayState.enabled,
                    enterEditModeRequest = shouldEnterHomeEdit,
                    onEnterEditModeRequestConsumed = {
                        shouldEnterHomeEdit = false
                    }
                )
                // 必吃榜匿名上传同意（未表态时弹一次，之后不再打扰）
                com.ahu.ahutong.ui.screen.canteen.CanteenConsentGate()
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
                val originalAccountId = rememberSaveable { SessionStore.currentUser()?.xh.orEmpty() }
                Login(
                    loginViewModel = loginViewModel,
                    onBack = { attempted ->
                        if (canCancelLogin(
                                originalAccountId,
                                SessionStore.currentUser()?.xh,
                                AhuSessionState.status.value,
                                attempted
                            ) && navController.previousBackStackEntry?.destination?.route == "settings"
                        ) {
                            navController.popBackStack()
                        } else if (navController.previousBackStackEntry != null) {
                            Toast.makeText(context, "请先完成登录，再进入应用", Toast.LENGTH_SHORT).show()
                        } else {
                            activity?.finish()
                        }
                    },
                    onLoggedIn = {
                        activity?.restartMainAfterLogin()
                    }
                )
            }
            animatedComposable("info") {
                Info(
                    scheduleViewModel = scheduleViewModel,
                    onBack = { navController.popBackStack() },
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
                    graduateAccountId = if (undergraduateEnabled) null else SessionStore.currentUser()?.xh,
                    onOpenCustomCourses = { navController.navigate("custom_courses") },
                    onEditCustomCourse = { id ->
                        navController.navigate("custom_courses?editId=${Uri.encode(id)}")
                    }
                )
            }
            animatedComposable(
                route = "custom_courses?editId={editId}",
                arguments = listOf(
                    navArgument("editId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                CustomCoursesScreen(
                    initialEditId = backStackEntry.arguments?.getString("editId"),
                    onBack = { navController.popBackStack() }
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
            animatedComposable("campus_notices") {
                CampusNoticeScreen(onBack = { navController.popBackStack() })
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
            animatedComposable("student_mail") {
                StudentMailScreen(onBack = { navController.popBackStack() })
            }
            animatedComposable("attendance_records") {
                AttendanceScreen(onBack = { navController.popBackStack() })
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
            animatedComposable("program_completion") {
                ProgramCompletion(onBack = { navController.popBackStack() })
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
            // 插件路由（内置 + 运行期 .ahup）：泛型一扇门——路由图只建一次，
            // 后装的插件也直接可达（按 id 在组合时解析，不在建图时枚举）
            animatedComposable("plugin/{pluginId}") { backStackEntry ->
                val pluginId = backStackEntry.arguments?.getString("pluginId").orEmpty()
                val plugin = plugins.firstOrNull { it.meta.id == pluginId }
                if (plugin != null) {
                    // 契约 v3：UI 边界是 View（插件自带 Compose 时 Composable 跨不了类加载器）
                    val scheme = MaterialTheme.colorScheme
                    val dark = androidx.compose.foundation.isSystemInDarkTheme()
                    val tokens = remember(scheme, dark) {
                        com.ahu.ahutong.core.plugin.PluginThemeTokens(
                            isDark = dark,
                            primary = scheme.primary.toArgb().toLong() and 0xFFFFFFFFL,
                            onPrimary = scheme.onPrimary.toArgb().toLong() and 0xFFFFFFFFL,
                            background = scheme.background.toArgb().toLong() and 0xFFFFFFFFL,
                            surface = scheme.surface.toArgb().toLong() and 0xFFFFFFFFL,
                            onSurface = scheme.onSurface.toArgb().toLong() and 0xFFFFFFFFL,
                            onSurfaceVariant = scheme.onSurfaceVariant.toArgb().toLong() and 0xFFFFFFFFL,
                            cornerRadiusDp = 28f,
                            bodyFontSizeSp = 14f
                        )
                    }
                    val pluginContext = remember(pluginId) {
                        com.ahu.ahutong.ui.plugin.RuntimePluginLoader.contextFor(pluginId) ?: context
                    }
                    val host = remember(pluginId, pluginContext) {
                        com.ahu.ahutong.ui.plugin.PluginHostServicesImpl(
                            pluginContext = pluginContext,
                            plugin = plugin,
                            onClose = { navController.popBackStack() },
                            themeTokensProvider = { tokens }
                        )
                    }
                    val onPluginBack: () -> Unit = {
                        if (!host.dispatchBack()) navController.popBackStack()
                    }
                    val pluginContent: @Composable () -> Unit = {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { viewContext ->
                                runCatching {
                                    plugin.createContentView(pluginContext, host)
                                }.onFailure {
                                    android.util.Log.e("PluginRoute", "创建插件视图失败：$pluginId", it)
                                }.getOrElse {
                                    android.widget.TextView(viewContext).apply {
                                        text = "插件渲染失败：$pluginId"
                                        setPadding(72, 72, 72, 72)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = { plugin.onDestroy() }
                        )
                    }
                    if (appUiTheme.usesLiquidGlass) {
                        pluginContent()
                    } else {
                        Column(Modifier.fillMaxSize().systemBarsPadding()) {
                            AppPageHeader(title = plugin.meta.title, onBack = onPluginBack)
                            Box(Modifier.weight(1f).fillMaxWidth()) { pluginContent() }
                        }
                    }
                    BackHandler(enabled = true, onBack = onPluginBack)
                    androidx.compose.runtime.DisposableEffect(pluginId) {
                        host.dispatchStart()
                        host.dispatchResume()
                        onDispose {
                            host.dispatchPause()
                            host.dispatchStop()
                        }
                    }
                } else {
                    val missingPlugin: @Composable () -> Unit = {
                        Text(
                            text = "插件未安装或已卸载",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (appUiTheme.usesLiquidGlass) {
                        missingPlugin()
                    } else {
                        Column(Modifier.fillMaxSize().systemBarsPadding()) {
                            AppPageHeader(title = "插件", onBack = { navController.popBackStack() })
                            missingPlugin()
                        }
                    }
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
            animatedComposable("profile") {
                ProfileScreen(
                    scheduleViewModel = scheduleViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            animatedComposable("settings__early_access") {
                EarlyAccessScreen(onBack = { navController.popBackStack() })
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
            animatedComposable("canteen_footprint") {
                CanteenFootprintScreen(onBack = { navController.popBackStack() })
            }
            animatedComposable("canteen_ranking") {
                CanteenRankingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFootprint = { navController.navigate("canteen_footprint") }
                )
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
                        onBack = { navController.popBackStack() },
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
        }
        if (!protectedRouteBlocked) {
        BottomNavBar(
            backdrop = backdrop,
            selectedRoute = currentRoute,
            onDestinationSelected = { route ->
                scope.launch { selectPrimaryDestination(route) }
            },
            scanEnabled = scanShortcutEnabled,
            onScanClick = { scanOpen = true }
        )
        }
        }

        if (showUpdateStrip || updateStripProgress.value > 0f) {
            val phase = when {
                mainViewModel.apkDownloading.value -> UpdateStripPhase.DOWNLOADING
                mainViewModel.apkLocalReady.value -> UpdateStripPhase.READY
                mainViewModel.apkDownloadFailed.value -> UpdateStripPhase.FAILED
                else -> UpdateStripPhase.AVAILABLE
            }
            val fade = (updateStripProgress.value / 0.7f).coerceIn(0f, 1f)
            val stripAlpha = fade * fade * (3f - 2f * fade)
            UpdateStatusStrip(
                phase = phase,
                progress = mainViewModel.apkProgress.value,
                mandatory = updateInfo?.force == true,
                onClick = {
                    if (phase == UpdateStripPhase.READY) mainViewModel.installLocalApk()
                    else mainViewModel.openApkUpdateDialog()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = UpdateStatusStripHeight.toPx() * (1f - updateStripProgress.value)
                        alpha = stripAlpha
                    },
            )
        }

        // 一键扫码：同页浮层（不跳导航页）。这里只把原文收下，分类与显式 Intent 直启下一步接。
        BackHandler(enabled = scanOpen) { scanOpen = false }
        AnimatedVisibility(
            visible = scanOpen,
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.94f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.96f)
        ) {
            ScanOverlay(
                onDecoded = { raw ->
                    when (val outcome = QrRouter.route(context, raw)) {
                        is QrRouter.Outcome.Handed -> {
                            outcome.note?.let {
                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            }
                            scanOpen = false
                        }

                        is QrRouter.Outcome.TargetMissing -> Toast.makeText(
                            context,
                            "未安装「${outcome.targetApp}」，或它没有响应",
                            Toast.LENGTH_LONG
                        ).show()

                        QrRouter.Outcome.Unrecognized -> Toast.makeText(
                            context,
                            "这张码不能直达（原文已显示在卡片里）",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onClose = { scanOpen = false }
            )
        }
        val productUiBlocked = !canUseBusinessPages || effectiveRoute == "login" || effectiveRoute == "setup" ||
            effectiveRoute == "splash" || effectiveRoute?.contains("deposit") == true ||
            effectiveRoute?.contains("recharge") == true ||
            effectiveRoute in setOf("electricity_pay", "electricity_recent_rooms", "electricity_alert_settings") ||
            suggestionOverlayBlocked || imeVisible
        EarlyAccessInvitationHost(
            eligible = canUseBusinessPages && currentRoute == "home" &&
                !productUiBlocked &&
                !mainViewModel.showApkUpdateDialog.value,
            accountRevision = sessionCheckRevision
        )
        SmartSuggestionHost(
            runtime = behaviorRuntime,
            backdrop = backdrop,
            blocked = productUiBlocked,
            hiddenForDiagnostics = diagnosticsRouteVisible,
            bottomSpacing = if (effectiveRoute in visiblePrimaryRoutes) {
                88.dp + updateInset
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
        }
    }
    }
}
