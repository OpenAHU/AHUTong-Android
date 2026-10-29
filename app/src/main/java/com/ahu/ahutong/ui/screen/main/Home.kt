package com.ahu.ahutong.ui.screen.main

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.R
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.notice.CampusNoticeRepository
import com.ahu.ahutong.data.schedule.gmis.GmisTimetableAdapter
import com.ahu.ahutong.data.crawler.gmis.PostgraduateScheduleRepository
import com.ahu.ahutong.data.schedule.PostgraduateTeachingWeek
import com.ahu.ahutong.ui.screen.main.home.selectHomeCourses
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.CancellationException
import com.ahu.ahutong.data.dao.HomeWidgetLayoutFamily
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.data.schedule.forDate
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.GlassBackdropContainer
import com.ahu.ahutong.ui.components.headerScrim
import com.ahu.ahutong.ui.components.headerBackdropSource
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.LocalLiquidGlassAmbientBackdrop
import com.ahu.ahutong.ui.screen.main.home.AtAGlance
import com.ahu.ahutong.ui.screen.main.home.HomeDateRow
import com.ahu.ahutong.ui.screen.main.home.HomeWeatherWidget
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetDragOverlay
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetLibrarySheet
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetPlacement
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetSlotLayout
import com.ahu.ahutong.ui.screen.main.home.HomeAcademicState
import com.ahu.ahutong.ui.screen.main.home.rememberHomeAcademicValue
import com.ahu.ahutong.ui.screen.main.home.homeWidgetEditGesture
import com.ahu.ahutong.ui.screen.main.home.CourseStrip
import com.ahu.ahutong.ui.screen.main.home.HomeScanButton
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.ahu.ahutong.ui.screen.main.home.rememberHomeWeatherConfig
import com.ahu.ahutong.ui.state.WeatherHomeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val HOME_REFRESH_INTERVAL_MS = 30_000L

private data class ActiveHomeWidgetDrag(
    val widgetId: String,
    val sourceSlot: Int,
    val topLeft: Offset,
    val size: IntSize
) {
    val center: Offset
        get() = topLeft + Offset(size.width / 2f, size.height / 2f)
}

/**
 * 条件卡片的进出动画：**纵向展开/收起 + 淡入淡出**。
 *
 * 卡片出现/消失时，父 Column 逐帧重测，所以上下内容是被"推着"平滑挪到位的；
 * 时长统一 200ms，进出同长（不一样长手感会不一致）。
 */
private val HomeCardEnter: EnterTransition =
    expandVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
        fadeIn(tween(200, easing = FastOutSlowInEasing))

private val HomeCardExit: ExitTransition =
    shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
        fadeOut(tween(200, easing = FastOutSlowInEasing))

@Composable
internal fun Home(
    discoveryViewModel: DiscoveryViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = hiltViewModel(),
    navController: NavHostController,
    behaviorRuntime: BehaviorPredictionRuntime,
    isActive: Boolean,
    undergraduateEnabled: Boolean,
    accountId: String?,
    academicState: HomeAcademicState,
    onOpenSchedule: () -> Unit = { navController.navigate("schedule") }
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentTime by remember { mutableStateOf(DebugClock.nowZonedDateTime().withSecond(0).withNano(0)) }
    val today = currentTime.toLocalDate()
    val currentMinutes = currentTime.hour * 60 + currentTime.minute
    val currentDateText = remember(today) {
        SimpleDateFormat("MM-dd / EE", Locale.CHINA).format(Date.from(currentTime.toInstant()))
    }
    val noticeAccountId = accountId
    val noticeSnapshot by CampusNoticeRepository.snapshot.collectAsState()
    val noticeUnreadCount = noticeSnapshot?.takeIf { it.accountId == noticeAccountId }?.unreadCount ?: 0
    LaunchedEffect(isActive, noticeAccountId) {
        if (isActive && AHUCache.isPrivacyAccepted() &&
            com.ahu.ahutong.data.session.SessionStore.isLoggedIn() && !noticeAccountId.isNullOrBlank()
        ) {
            CampusNoticeRepository.open(noticeAccountId)
            CampusNoticeRepository.requestSyncIfDue()
        }
    }
    val graduateAccountId = if (undergraduateEnabled) null else accountId
    val graduateRepository = remember { PostgraduateScheduleRepository.instance }
    val graduateCacheRevision by graduateRepository.revision.collectAsState()
    val graduateWeekRevision by AHUCache.postgraduateWeekUpdates().collectAsState()
    val loadedGraduateCache by rememberHomeAcademicValue(academicState.graduate, graduateAccountId, graduateCacheRevision) {
        graduateAccountId to withContext(Dispatchers.IO) {
            graduateAccountId?.let(graduateRepository::cachedCurrent)
        }
    }
    val graduateCacheReady = loadedGraduateCache != null && loadedGraduateCache?.first == graduateAccountId
    val graduateSnapshot = loadedGraduateCache?.takeIf { it.first == graduateAccountId }?.second
    LaunchedEffect(graduateAccountId, graduateCacheReady, graduateSnapshot == null) {
        val account = graduateAccountId ?: return@LaunchedEffect
        if (!graduateCacheReady) return@LaunchedEffect
        if (graduateSnapshot == null) {
            try {
                withContext(Dispatchers.IO) { graduateRepository.current(account) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("PostgraduateHome", "Graduate timetable unavailable (${e.javaClass.simpleName})")
            }
        }
    }
    val graduateGrid = remember(graduateSnapshot?.timetable) {
        graduateSnapshot?.timetable?.let(GmisTimetableAdapter::adapt)
    }
    val graduateTerm = graduateSnapshot?.selectedTerm
    val graduateAnchor = remember(graduateAccountId, graduateTerm?.code, graduateWeekRevision) {
        graduateTerm?.let {
            PostgraduateTeachingWeek.parseStored(AHUCache.getPostgraduateWeekStart(it.code))
        }
    }
    val graduateConfig = remember(graduateAnchor, today, graduateTerm?.code) {
        graduateAnchor?.let { monday ->
            ScheduleConfigBean().apply {
                val actualWeek = PostgraduateTeachingWeek.weekOn(monday, today)
                week = actualWeek.coerceIn(1, PostgraduateTeachingWeek.MAX_WEEK)
                weekDay = today.dayOfWeek.value
                startTime = Date.from(monday.atStartOfDay(ZoneId.systemDefault()).toInstant())
                isInSemester = actualWeek in 1..PostgraduateTeachingWeek.MAX_WEEK &&
                    (graduateTerm?.selected == true || graduateSnapshot?.terms?.none { it.selected } == true)
            }
        }
    }
    val homeAcademicReady = undergraduateEnabled || (graduateSnapshot != null && graduateConfig != null)
    val density = LocalDensity.current
    val undergraduateSchedule = scheduleViewModel.schedule.observeAsState().value?.valueOrNull() ?: emptyList()
    val scheduleConfig by scheduleViewModel.scheduleConfig.observeAsState()
    val holidays by scheduleViewModel.scheduleHolidays.observeAsState(emptyMap())
    LaunchedEffect(isActive, today, lifecycleOwner) {
        if (isActive) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    scheduleViewModel.loadHolidaysForDates(setOf(today, today.plusDays(1)))
                    delay(5 * 60_000L)
                }
            }
        }
    }
    val localScheduleConfig by rememberHomeAcademicValue(
        academicState.undergraduate, scheduleConfig, undergraduateEnabled, today
    ) {
        if (!undergraduateEnabled) null else scheduleConfig ?: withContext(Dispatchers.IO) {
            CurrentWeekResolver.resolveLocalConfig(today)?.config
        }
    }
    val baseScheduleConfig = if (undergraduateEnabled) scheduleConfig ?: localScheduleConfig else graduateConfig
    val effectiveScheduleConfig = remember(
        baseScheduleConfig?.startTime?.time, baseScheduleConfig?.week,
        baseScheduleConfig?.isShowAll, baseScheduleConfig?.isInSemester, today, undergraduateEnabled
    ) {
        baseScheduleConfig?.forDate(today,
            weekCount = if (undergraduateEnabled) 20 else PostgraduateTeachingWeek.MAX_WEEK)
    }
    val isInSemester = effectiveScheduleConfig?.isInSemester != false
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()
    LaunchedEffect(isActive) {
        if (isActive) scheduleViewModel.onHomeEntered()
    }
    // 主页排版全主题统一为 Radiant 方案（曜光居中 Hero 布局），主题只换材质皮肤
    val layoutFamily = HomeWidgetLayoutFamily.RADIANT
    val slotCount = HomeWidgetRegistry.slotCountRadiant
    val plugins by com.ahu.ahutong.ui.plugin.PluginRegistry.plugins.collectAsState()
    val homeWidgets = remember(plugins, undergraduateEnabled) {
        HomeWidgetRegistry.availableWidgets(true, undergraduateEnabled)
    }
    val knownWidgetIds = remember(homeWidgets) {
        homeWidgets.mapTo(mutableSetOf()) { it.id }
    }
    val homeCourses = remember(
        undergraduateSchedule, graduateGrid, undergraduateEnabled, effectiveScheduleConfig, currentMinutes
    ) {
        selectHomeCourses(
            undergraduateCourses = undergraduateSchedule,
            graduateCourses = graduateGrid?.courses.orEmpty(),
            undergraduateEnabled = undergraduateEnabled,
            config = effectiveScheduleConfig,
            currentMinutes = currentMinutes
        )
    }
    val todayCourses = homeCourses.today
    var isEditingHome by remember(noticeAccountId) { mutableStateOf(false) }
    var addingWidgetSlot by remember(noticeAccountId) { mutableStateOf<Int?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    var homeWidgetSlots by remember(noticeAccountId) {
        mutableStateOf(normalizeHomeWidgetSlots(HomeWidgetPlacement.currentSlots(), slotCount, knownWidgetIds))
    }
    val homeWidgetRevision by HomeWidgetPlacement.revision.collectAsState()
    LaunchedEffect(homeWidgetRevision, noticeAccountId, knownWidgetIds) {
        homeWidgetSlots = normalizeHomeWidgetSlots(HomeWidgetPlacement.currentSlots(), slotCount, knownWidgetIds)
    }
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var rootTopLeft by remember { mutableStateOf(Offset.Zero) }
    var activeDrag by remember { mutableStateOf<ActiveHomeWidgetDrag?>(null) }
    val dropSlopPx = remember(density) { with(density) { 48.dp.toPx() } }
    val highlightedSlot = activeDrag?.let {
        findHomeWidgetDropSlot(
            drag = it,
            slotBounds = slotBounds,
            dropSlopPx = dropSlopPx,
            slotCount = slotCount
        )
    }
    val weatherHomeConfig = rememberHomeWeatherConfig(noticeAccountId, isActive)

    fun saveHomeWidgetSlots(slots: List<String?>) {
        val normalizedSlots = normalizeHomeWidgetSlots(slots, slotCount, knownWidgetIds)
        homeWidgetSlots = normalizedSlots
        AHUCache.saveHomeWidgetSlots(layoutFamily, normalizedSlots)
    }

    fun enterHomeEditMode() {
        if (isActive && !isEditingHome) {
            isEditingHome = true
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            behaviorRuntime.recordActionIntentAsync(AppActionId.EDIT_HOME, ActionSource.ORGANIC)
        }
    }

    fun startDrag(widgetId: String, sourceSlot: Int, bounds: Rect) {
        if (!isEditingHome || addingWidgetSlot != null) return
        if (homeWidgetSlots.getOrNull(sourceSlot - 1) != widgetId) return
        activeDrag = ActiveHomeWidgetDrag(
            widgetId = widgetId,
            sourceSlot = sourceSlot,
            topLeft = bounds.topLeft,
            size = IntSize(
                width = bounds.width.roundToInt().coerceAtLeast(1),
                height = bounds.height.roundToInt().coerceAtLeast(1)
            )
        )
    }

    fun stopDrag() {
        val drag = activeDrag ?: return
        activeDrag = null
        if (!isEditingHome) return
        val nextSlots = homeWidgetSlots.toMutableList()
        val targetSlot = findHomeWidgetDropSlot(
            drag = drag,
            slotBounds = slotBounds,
            dropSlopPx = dropSlopPx,
            slotCount = slotCount
        )

        if (targetSlot != null && drag.sourceSlot != targetSlot &&
            nextSlots.getOrNull(drag.sourceSlot - 1) == drag.widgetId
        ) {
            nextSlots.removeAt(drag.sourceSlot - 1)
            nextSlots.add(targetSlot - 1, drag.widgetId)
            saveHomeWidgetSlots(nextSlots)
            behaviorRuntime.recordCommittedMutationAsync(
                MutationId.HOME_WIDGET_MOVED,
                drag.sourceSlot,
                targetSlot,
                coarseValueBucket = drag.widgetId.uppercase()
            )
        }
    }

    fun addHomeWidget(widgetId: String) {
        if (!isEditingHome || widgetId !in knownWidgetIds) return
        val targetSlot = addingWidgetSlot ?: return
        val nextSlots = homeWidgetSlots.toMutableList()
        val targetIndex = targetSlot - 1
        if (targetIndex !in nextSlots.indices || nextSlots[targetIndex] != null || widgetId in nextSlots) return
        nextSlots[targetIndex] = widgetId
        saveHomeWidgetSlots(nextSlots)
        addingWidgetSlot = null
        behaviorRuntime.recordCommittedMutationAsync(
            MutationId.HOME_WIDGET_ADDED,
            null,
            widgetId,
            coarseValueBucket = widgetId.uppercase()
        )
    }

    fun removeHomeWidget(slotIndex: Int) {
        if (!isEditingHome || activeDrag != null) return
        val nextSlots = homeWidgetSlots.toMutableList()
        if (slotIndex !in 1..nextSlots.size) return
        val removedWidget = nextSlots[slotIndex - 1] ?: return
        nextSlots[slotIndex - 1] = null
        saveHomeWidgetSlots(nextSlots)
        behaviorRuntime.recordCommittedMutationAsync(
            MutationId.HOME_WIDGET_REMOVED,
            removedWidget,
            null,
            coarseValueBucket = removedWidget.uppercase()
        )
    }

    fun exitHomeEditMode() {
        activeDrag = null
        addingWidgetSlot = null
        isEditingHome = false
    }

    BackHandler(enabled = isEditingHome) {
        if (addingWidgetSlot != null) addingWidgetSlot = null else exitHomeEditMode()
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            exitHomeEditMode()
        }
    }
    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && AHUCache.getMockData()) {
            discoveryViewModel.loadActivityBean()
            scheduleViewModel.loadConfig()
            scheduleViewModel.refreshSchedule(isRefresh = true)
        }
    }
    LaunchedEffect(isActive, lifecycleOwner) {
        if (!isActive) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
          while (true) {
            val now = withContext(Dispatchers.IO) { DebugClock.nowZonedDateTime() }
            currentTime = now.withSecond(0).withNano(0)
            // 进入/回到主页先立即对一次余额，别把首次刷新拖到 30 秒后。
            discoveryViewModel.refreshCardBalance()
            delay(DebugClock.nextCalendarRefreshDelayMillis(now, HOME_REFRESH_INTERVAL_MS))
          }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            exitHomeEditMode()
        }
    }
    val showHeaderWeather = !isEditingHome && weatherHomeConfig.showOnHome
    var headerContentHeight by remember(showHeaderWeather, isEditingHome, density) {
        // 天气与扫码钮现在同处日期行（同一控件行），不再多占一行
        mutableStateOf(if (isEditingHome) 48.dp else 60.dp)
    }
    GlassBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        CompositionLocalProvider(
            LocalLiquidGlassAmbientBackdrop provides backdrop
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .appLiquidGlassSceneBackground(96.n1 withNight 10.n1)
                .onGloballyPositioned { rootTopLeft = it.boundsInRoot().topLeft }
                .homeWidgetEditGesture(
                    enabled = isActive && !isEditingHome,
                    onEnterEdit = ::enterHomeEditMode
                )
        ) {
        // 标题栏模糊采样层：只包滚动内容（头部自身在层外，避免自采样）；
        // 模糊关闭时 headerBackdropSource 零开销直通
        val headerBlurLayer = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
        // 标题栏常驻控件是"固定悬浮"层：采样源 = 壁纸层 + 滚动内容层，
        // 控件底下透出的是滚过去的卡片（与标题栏遮罩同一套组合层），而不只是背景色。
        val headerControlsBackdrop = com.kyant.backdrop.backdrops.rememberCombinedBackdrop(
            LocalLiquidGlassAmbientBackdrop.current,
            headerBlurLayer
        )
        val scanButton: @Composable () -> Unit = {
            HomeScanButton(
                onClick = { navController.navigate("scan") { launchSingleTop = true } },
                backdrop = headerControlsBackdrop
            )
        }
        val trailingContent: @Composable RowScope.() -> Unit = {
            if (BuildConfig.DEBUG) {
                DebugBuildBadge()
            }
            if (showHeaderWeather) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeWeatherWidget(
                        onClick = { navController.navigate("weather") },
                        config = weatherHomeConfig,
                        mode = WeatherHomeMode.Compact,
                        backdrop = headerControlsBackdrop
                    )
                    scanButton()
                }
            } else if (isEditingHome) {
                TextButton(onClick = ::exitHomeEditMode) { Text("完成") }
            } else {
                scanButton()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .headerBackdropSource(headerBlurLayer)
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(
                    top = headerContentHeight,
                    bottom = 96.dp
                ),
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = homeAcademicReady,
                enter = HomeCardEnter,
                exit = HomeCardExit
            ) {
                AtAGlance(
                    todayCourses = todayCourses,
                    currentMinutes = currentMinutes,
                    currentDateText = currentDateText,
                    onOpenSchedule = onOpenSchedule,
                    isInSemester = isInSemester,
                    emptyCourseText = if (undergraduateEnabled) "已全部上完" else "今日无课",
                    enabled = !isEditingHome,
                    trailingContent = trailingContent
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 今日课程全部结束 → 课程条切换为明日课程（Hero 大字仍按今日算，显示空闲）
            AnimatedVisibility(
                // 配置没就绪时 currentWeek/weekDay 只是兜底值，先别渲染——否则会"先出现一份周一的课，再缩回去"
                visible = homeCourses.strip.isNotEmpty(),
                enter = HomeCardEnter,
                exit = HomeCardExit
            ) {
                // Spacer 必须一起进动画，否则卡片滑进来了、间距却是瞬间跳的
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    // 课程条（滚轮三区）替代原纵向列表——全主题生效
                    CourseStrip(
                        todayCourses = homeCourses.strip,
                        currentMinutes = currentMinutes,
                        onOpenSchedule = onOpenSchedule,
                        isTomorrow = homeCourses.isTomorrow,
                        holiday = holidays[if (homeCourses.isTomorrow) today.plusDays(1) else today]
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HomeWidgetSlotLayout(
                balance = discoveryViewModel.balance,
                transitionBalance = discoveryViewModel.transitionBalance,
                onRefreshBalance = discoveryViewModel::refreshCardBalance,
                navController = navController,
                isHomeActive = isActive,
                slots = homeWidgetSlots,
                noticeUnreadCount = noticeUnreadCount,
                isEditing = isEditingHome,
                highlightedSlot = highlightedSlot,
                draggingWidgetId = activeDrag?.widgetId,
                onRemoveHomeWidget = ::removeHomeWidget,
                onAddHomeWidget = { slotIndex ->
                    if (isEditingHome && activeDrag == null &&
                        slotIndex in 1..homeWidgetSlots.size && homeWidgetSlots[slotIndex - 1] == null
                    ) addingWidgetSlot = slotIndex
                },
                onExitEdit = ::exitHomeEditMode,
                onSlotPositioned = { slotIndex, bounds ->
                    slotBounds[slotIndex] = bounds
                },
                onHomeWidgetDragStarted = { widgetId, slotIndex, bounds ->
                    startDrag(widgetId, slotIndex, bounds)
                },
                onHomeWidgetDragged = { dragAmount ->
                    activeDrag = activeDrag?.let {
                        it.copy(topLeft = it.topLeft + dragAmount)
                    }
                },
                onHomeWidgetDragStopped = ::stopDrag,
                onHomeWidgetDragCancelled = { activeDrag = null }
            )
        }

        // 曜光固定头部（遮罩 + 日期行），全主题统一；
        // 关玻璃开→纯色渐变（降级），液态可用→均匀模糊+渐变 scrim（方案 a）
        run {
            val headerBackground = if (LocalIsLiquidGlassEnabled.current) {
                MaterialTheme.colorScheme.surfaceContainerLowest
            } else {
                MaterialTheme.colorScheme.surface
            }
            // 遮罩画在独立的背景层上，内容层不再受 scrim 节点的绘制范围限制：
            // 天气胶囊/扫码钮的投影落到标题栏下沿时不会被裁掉一条边。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(20f)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .headerScrim(headerBackground, headerBlurLayer)
                )
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 12.dp)
                ) {
                    Box(Modifier.onSizeChanged {
                        headerContentHeight = with(density) { it.height.toDp() } + 12.dp
                    }) {
                        HomeDateRow(dateText = currentDateText, trailingContent = trailingContent)
                    }
                }
            }
        }

        val availableWidgets = remember(homeWidgets, homeWidgetSlots) {
            val placedWidgetIds = homeWidgetSlots.filterNotNull().toSet()
            homeWidgets.filter { it.id !in placedWidgetIds }
        }
        if (isEditingHome && addingWidgetSlot != null) {
            HomeWidgetLibrarySheet(
                availableWidgets = availableWidgets,
                onDismiss = { addingWidgetSlot = null },
                onLibraryWidgetClick = ::addHomeWidget
            )
        }

        activeDrag?.let { drag ->
            HomeWidgetRegistry.widgetById[drag.widgetId]?.let { spec ->
                HomeWidgetDragOverlay(
                    spec = spec,
                    topLeft = drag.topLeft,
                    size = drag.size,
                    rootTopLeft = rootTopLeft
                )
            }
        }
        }
        }
    }
}

@Composable
private fun DebugBuildBadge() {
    Surface(
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .height(28.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.BugReport,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.debug_build_badge),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun normalizeHomeWidgetSlots(
    slots: List<String?>,
    slotCount: Int,
    knownIds: Set<String>
): List<String?> {
    val seen = mutableSetOf<String>()
    return List(slotCount) { index ->
        val id = slots.getOrNull(index)?.takeIf { it in knownIds }
        if (id != null && seen.add(id)) id else null
    }
}

private fun findHomeWidgetDropSlot(
    drag: ActiveHomeWidgetDrag,
    slotBounds: Map<Int, Rect>,
    dropSlopPx: Float,
    slotCount: Int
): Int? {
    val center = drag.center
    return slotBounds
        .filterKeys { it in 1..slotCount }
        .mapNotNull { (slotIndex, bounds) ->
            if (!bounds.expandedBy(dropSlopPx).contains(center)) return@mapNotNull null
            slotIndex to bounds.centerDistanceTo(center)
        }
        .minByOrNull { it.second }
        ?.first
}

private fun Rect.expandedBy(padding: Float): Rect {
    return Rect(
        left = left - padding,
        top = top - padding,
        right = right + padding,
        bottom = bottom + padding
    )
}

private fun Rect.centerDistanceTo(point: Offset): Float {
    return hypot(center.x - point.x, center.y - point.y)
}
