package com.ahu.ahutong.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.R
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.ui.components.GlassBackdropContainer
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.screen.main.home.AtAGlance
import com.ahu.ahutong.ui.screen.main.home.HomeDateRow
import com.ahu.ahutong.ui.screen.main.home.HomeWeatherWidget
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetDragOverlay
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetLibrarySheet
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetSlotLayout
import com.ahu.ahutong.ui.screen.main.home.TodayCourseList
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.ahu.ahutong.ui.state.WeatherHomeConfig
import com.ahu.ahutong.ui.state.WeatherHomeMode
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val HOME_REFRESH_INTERVAL_MS = 30_000L

private data class ActiveHomeWidgetDrag(
    val widgetId: String,
    val sourceSlot: Int?,
    val topLeft: Offset,
    val size: IntSize
) {
    val center: Offset
        get() = topLeft + Offset(size.width / 2f, size.height / 2f)
}

@Composable
fun Home(
    discoveryViewModel: DiscoveryViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = viewModel(),
    navController: NavHostController,
    behaviorRuntime: BehaviorPredictionRuntime,
    homeEditEnabled: Boolean = false,
    enterEditModeRequest: Boolean = false,
    onEnterEditModeRequestConsumed: () -> Unit = {}
) {
    val density = LocalDensity.current
    val schedule = scheduleViewModel.schedule.observeAsState().value?.getOrNull() ?: emptyList()
    val scheduleConfig by scheduleViewModel.scheduleConfig.observeAsState()
    val effectiveScheduleConfig = scheduleConfig ?: CurrentWeekResolver.resolveLocalConfig()?.config
    val isInSemester = effectiveScheduleConfig?.isInSemester != false
    val currentWeek = effectiveScheduleConfig?.week ?: 1
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()
    val todayCourses = if (isInSemester) {
        schedule
            .filter { effectiveScheduleConfig?.week in it.startWeek..it.endWeek }
            .filter { it.weekday == (effectiveScheduleConfig?.weekDay ?: 1) }
            .filter {
                if (currentWeek in it.weekIndexes) {
                    true
                } else {
                    currentWeek % 2 == it.startWeek % 2
                }
            }
            .sortedBy { it.startTime }
    } else {
        emptyList()
    }
    val radiant = isRadiantUi
    val slotCount = if (radiant) HomeWidgetRegistry.slotCountRadiant else HomeWidgetRegistry.slotCountClassic
    val knownIds = HomeWidgetRegistry.availableWidgets(radiant).map { it.id }.toSet()
    var currentMinutes by remember { mutableIntStateOf(DebugClock.currentMinutes()) }
    var isEditingHome by remember { mutableStateOf(false) }
    var homeWidgetSlots by remember(radiant) {
        val baseSlots = if (radiant && !AHUCache.hasCustomHomeWidgetSlots()) {
            HomeWidgetRegistry.defaultSlotsRadiant
        } else {
            AHUCache.getHomeWidgetSlots()
        }
        mutableStateOf(normalizeHomeWidgetSlots(baseSlots, slotCount, knownIds))
    }
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var libraryBounds by remember { mutableStateOf<Rect?>(null) }
    var rootTopLeft by remember { mutableStateOf(Offset.Zero) }
    var activeDrag by remember { mutableStateOf<ActiveHomeWidgetDrag?>(null) }
    val dropSlopPx = with(density) { 48.dp.toPx() }
    val highlightedSlot = activeDrag?.let {
        findHomeWidgetDropSlot(
            drag = it,
            slots = homeWidgetSlots,
            slotBounds = slotBounds,
            dropSlopPx = dropSlopPx,
            slotCount = slotCount
        )
    }
    val weatherHomeConfig = WeatherHomeConfig.fromCache()

    fun saveHomeWidgetSlots(slots: List<String?>) {
        val normalizedSlots = normalizeHomeWidgetSlots(slots, slotCount, knownIds)
        homeWidgetSlots = normalizedSlots
        AHUCache.saveHomeWidgetSlots(normalizedSlots)
    }

    fun enterHomeEditMode() {
        if (homeEditEnabled) {
            isEditingHome = true
        }
    }

    fun startDrag(widgetId: String, sourceSlot: Int?, bounds: Rect) {
        if (!homeEditEnabled) return
        enterHomeEditMode()
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
        val dragCenter = drag.center
        val nextSlots = homeWidgetSlots.toMutableList()
        val targetSlot = findHomeWidgetDropSlot(
            drag = drag,
            slots = homeWidgetSlots,
            slotBounds = slotBounds,
            dropSlopPx = dropSlopPx,
            slotCount = slotCount
        )

        if (drag.sourceSlot != null && libraryBounds?.contains(dragCenter) == true) {
            nextSlots[drag.sourceSlot - 1] = null
            saveHomeWidgetSlots(nextSlots)
            behaviorRuntime.recordCommittedMutationAsync(
                MutationId.HOME_WIDGET_REMOVED,
                drag.widgetId,
                null,
                coarseValueBucket = drag.widgetId.uppercase()
            )
        } else if (targetSlot != null) {
            val targetIndex = targetSlot - 1
            val sourceSlot = drag.sourceSlot

            if (sourceSlot == null) {
                if (nextSlots[targetIndex] == null) {
                    nextSlots[targetIndex] = drag.widgetId
                    saveHomeWidgetSlots(nextSlots)
                    behaviorRuntime.recordCommittedMutationAsync(
                        MutationId.HOME_WIDGET_ADDED,
                        null,
                        drag.widgetId,
                        coarseValueBucket = drag.widgetId.uppercase()
                    )
                }
            } else if (sourceSlot != targetSlot) {
                val sourceIndex = sourceSlot - 1
                val targetWidget = nextSlots[targetIndex]
                nextSlots[targetIndex] = drag.widgetId
                nextSlots[sourceIndex] = targetWidget
                saveHomeWidgetSlots(nextSlots)
                behaviorRuntime.recordCommittedMutationAsync(
                    MutationId.HOME_WIDGET_MOVED,
                    sourceSlot,
                    targetSlot,
                    coarseValueBucket = drag.widgetId.uppercase()
                )
            }
        }

        activeDrag = null
    }

    fun addWidgetToFirstEmptySlot(widgetId: String) {
        val nextSlots = homeWidgetSlots.toMutableList()
        val targetIndex = nextSlots.indexOfFirst { it == null }
        if (targetIndex == -1 || widgetId in nextSlots) return
        nextSlots[targetIndex] = widgetId
        saveHomeWidgetSlots(nextSlots)
        behaviorRuntime.recordCommittedMutationAsync(
            MutationId.HOME_WIDGET_ADDED,
            null,
            widgetId,
            coarseValueBucket = widgetId.uppercase()
        )
    }

    fun removeHomeWidget(slotIndex: Int) {
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
        isEditingHome = false
    }

    BackHandler(enabled = isEditingHome) {
        exitHomeEditMode()
    }

    LaunchedEffect(Unit) {
        if (!enterEditModeRequest) {
            exitHomeEditMode()
        }
        discoveryViewModel.loadActivityBean()

        repeat(2 - discoveryViewModel.visibilities.size) {
            delay(100)
            discoveryViewModel.visibilities += discoveryViewModel.visibilities.lastIndex + 1
        }
    }
    LaunchedEffect(enterEditModeRequest) {
        if (enterEditModeRequest) {
            activeDrag = null
            enterHomeEditMode()
            onEnterEditModeRequestConsumed()
        }
    }
    LaunchedEffect(homeEditEnabled) {
        if (!homeEditEnabled) {
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
    LaunchedEffect(Unit) {
        while (true) {
            delay(HOME_REFRESH_INTERVAL_MS)
            currentMinutes = DebugClock.currentMinutes()
            discoveryViewModel.refreshCardBalance()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            exitHomeEditMode()
        }
    }
    val trailing: @Composable RowScope.() -> Unit = {
        if (BuildConfig.DEBUG) {
            DebugBuildBadge()
        }
        if (
            !isEditingHome &&
            weatherHomeConfig.showOnHome &&
            weatherHomeConfig.mode == WeatherHomeMode.Compact
        ) {
            HomeWeatherWidget(
                onClick = { navController.navigate("weather") },
                modifier = Modifier.padding(start = 12.dp),
                config = weatherHomeConfig,
                mode = WeatherHomeMode.Compact
            )
        }
    }
    GlassBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootTopLeft = it.boundsInRoot().topLeft }
            .pointerInput(isEditingHome, homeEditEnabled) {
                if (isEditingHome) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Final
                        )
                        val start = down.position
                        var shouldExit = !down.isConsumed
                        var waitingForUp = true
                        while (waitingForUp) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                waitingForUp = false
                            } else {
                                if (change.isConsumed ||
                                    (change.position - start).getDistance() > viewConfiguration.touchSlop
                                ) {
                                    shouldExit = false
                                }
                                if (!change.pressed) {
                                    waitingForUp = false
                                }
                            }
                        }
                        if (shouldExit) {
                            exitHomeEditMode()
                        }
                    }
                } else {
                    detectTapGestures(
                        onLongPress = {
                            enterHomeEditMode()
                        }
                    )
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(
                    top = if (radiant) 48.dp else 0.dp,
                    bottom = if (isEditingHome) 520.dp else 96.dp
                ),
            verticalArrangement = if (radiant) Arrangement.Center else Arrangement.spacedBy(24.dp)
        ) {
            AtAGlance(
                todayCourses = todayCourses,
                currentMinutes = currentMinutes,
                navController = navController,
                isInSemester = isInSemester,
                enabled = !isEditingHome,
                trailingContent = trailing
            )

            if (radiant) Spacer(modifier = Modifier.height(12.dp))
            if (todayCourses.isNotEmpty()) {
                if (radiant) Spacer(modifier = Modifier.height(16.dp))
                SlideInContent(visible = 0 in discoveryViewModel.visibilities) {
                    TodayCourseList(
                        todayCourses = todayCourses,
                        currentMinutes = currentMinutes,
                        navController = navController,
                        enabled = !isEditingHome
                    )
                }
            }

            if (weatherHomeConfig.showOnHome && weatherHomeConfig.mode == WeatherHomeMode.Detailed) {
                if (radiant) Spacer(modifier = Modifier.height(20.dp))
                SlideInContent(visible = !isEditingHome) {
                    HomeWeatherWidget(
                        onClick = { navController.navigate("weather") },
                        config = weatherHomeConfig,
                        mode = WeatherHomeMode.Detailed
                    )
                }
            }

            if (radiant) Spacer(modifier = Modifier.height(8.dp))
            SlideInContent(visible = 1 in discoveryViewModel.visibilities) {
                HomeWidgetSlotLayout(
                    balance = discoveryViewModel.balance,
                    transitionBalance = discoveryViewModel.transitionBalance,
                    onRefreshBalance = discoveryViewModel::refreshCardBalance,
                    navController = navController,
                    backdrop = backdrop,
                    slots = homeWidgetSlots,
                    isEditing = isEditingHome,
                    highlightedSlot = highlightedSlot,
                    draggingWidgetId = activeDrag?.widgetId,
                    onEnterEdit = ::enterHomeEditMode,
                    onHomeWidgetClick = ::removeHomeWidget,
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
                    onHomeWidgetDragStopped = ::stopDrag
                )
            }
        }

        if (radiant) {
            val headerBg = if (LocalIsLiquidGlassEnabled.current) {
                MaterialTheme.colorScheme.surfaceContainerLowest
            } else {
                MaterialTheme.colorScheme.surface
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(20f)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to headerBg,
                                0.35f to headerBg,
                                0.68f to headerBg.copy(alpha = 0.85f),
                                1f to headerBg.copy(alpha = 0f)
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            ) {
                HomeDateRow(
                    trailingContent = trailing
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        val placedWidgetIds = homeWidgetSlots.filterNotNull().toSet()
        val availableWidgets = HomeWidgetRegistry.availableWidgets(radiant).filter { it.id !in placedWidgetIds }
        val isDraggingFromLibrary = activeDrag != null && activeDrag?.sourceSlot == null
        HomeWidgetLibrarySheet(
            visible = isEditingHome,
            hiddenDuringLibraryDrag = isDraggingFromLibrary,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
            availableWidgets = availableWidgets,
            onDismiss = {
                activeDrag = null
                isEditingHome = false
            },
            onBoundsChanged = { libraryBounds = it },
            onLibraryWidgetClick = ::addWidgetToFirstEmptySlot,
            onLibraryWidgetDragStarted = { widgetId, bounds ->
                startDrag(widgetId, null, bounds)
            },
            onLibraryWidgetDragged = { dragAmount ->
                activeDrag = activeDrag?.let {
                    it.copy(topLeft = it.topLeft + dragAmount)
                }
            },
            onLibraryWidgetDragStopped = ::stopDrag
        )

        activeDrag?.let { drag ->
            HomeWidgetRegistry.widgetById[drag.widgetId]?.let { spec ->
                val previewSlot = if (drag.sourceSlot == null) {
                    highlightedSlot
                        ?: homeWidgetSlots.indexOfFirst { it == null }
                            .takeIf { it != -1 }
                            ?.let { it + 1 }
                } else {
                    null
                }
                val previewBounds = previewSlot?.let { slotBounds[it] }
                val previewSize = previewBounds?.let {
                    IntSize(
                        width = it.width.roundToInt().coerceAtLeast(1),
                        height = it.height.roundToInt().coerceAtLeast(1)
                    )
                } ?: drag.size
                val previewTopLeft = if (previewBounds != null) {
                    drag.center - Offset(previewSize.width / 2f, previewSize.height / 2f)
                } else {
                    drag.topLeft
                }

                HomeWidgetDragOverlay(
                    spec = spec,
                    topLeft = previewTopLeft,
                    size = previewSize,
                    rootTopLeft = rootTopLeft,
                    backdrop = backdrop
                )
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
    slots: List<String?>,
    slotBounds: Map<Int, Rect>,
    dropSlopPx: Float,
    slotCount: Int
): Int? {
    val center = drag.center
    return slotBounds
        .filterKeys { it in 1..slotCount }
        .mapNotNull { (slotIndex, bounds) ->
            if (!bounds.expandedBy(dropSlopPx).contains(center)) return@mapNotNull null
            if (drag.sourceSlot == null && slots.getOrNull(slotIndex - 1) != null) return@mapNotNull null
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

@Composable
fun SlideInContent(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        content = content
    )
}
