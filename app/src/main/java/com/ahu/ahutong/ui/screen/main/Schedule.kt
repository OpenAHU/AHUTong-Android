package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.GlassBackdropContainer
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.LocalLiquidGlassAmbientBackdrop
import com.ahu.ahutong.ui.components.liquidGlassSurface
import com.ahu.ahutong.ui.components.liquidGlassTint
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.screen.main.schedule.CourseCard
import com.ahu.ahutong.ui.screen.main.schedule.CourseCardSpec
import com.ahu.ahutong.ui.screen.main.schedule.CourseDetailDialog
import com.ahu.ahutong.ui.screen.main.schedule.courseTonalPalettes
import com.ahu.ahutong.ui.screen.main.schedule.courseScheduleDescription
import com.ahu.ahutong.ui.screen.main.schedule.scheduleDayDescription
import com.ahu.ahutong.ui.screen.main.schedule.schedulePeriodDescription
import com.ahu.ahutong.ui.screen.main.schedule.shortScheduleLocation
import com.ahu.ahutong.ui.screen.main.schedule.weekRangeText
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.Hct.Companion.toHct
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.n2
import com.kyant.monet.toColor
import com.kyant.monet.toSrgb
import com.kyant.monet.withNight
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import android.widget.Toast
import com.ahu.ahutong.ui.screen.main.schedule.CourseCardSpec.cellSpacing
import com.ahu.ahutong.personalization.ui.rememberBehaviorActionReporter
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain

@Composable
fun Schedule(
    scheduleViewModel: ScheduleViewModel = hiltViewModel(),
    behaviorRuntime: BehaviorPredictionRuntime,
    isActive: Boolean = true
) {
    val behaviorReporter = rememberBehaviorActionReporter()
    val scope = rememberCoroutineScope()
    val scheduleConfig by scheduleViewModel.scheduleConfig.observeAsState()
    val currentWeekday = scheduleConfig?.weekDay ?: 1
    var currentWeek by rememberSaveable { mutableStateOf(scheduleConfig?.week ?: 1) }
    val pagerState = rememberPagerState(
        initialPage = (currentWeek - 1).coerceAtLeast(0),
        pageCount = { 20 }
    )
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentWeek - 3).coerceAtLeast(0)
    )
    val scheduleResult = scheduleViewModel.schedule.observeAsState().value
    val nextScheduleResult = scheduleViewModel.nextSchedule.observeAsState().value
    val scheduleFetchedAt by scheduleViewModel.scheduleFetchedAt.observeAsState()
    val isScheduleRefreshing by scheduleViewModel.isScheduleRefreshing.observeAsState(false)
    val scheduleRefreshError by scheduleViewModel.scheduleRefreshError.observeAsState()
    var isPreviewNextSemester by rememberSaveable { mutableStateOf(false) }
    var isOverviewSchedule by rememberSaveable { mutableStateOf(false) }
    var isSettingsVisible by rememberSaveable { mutableStateOf(false) }
    var renderCourseCards by remember { mutableStateOf(true) }
    var hasRenderedCards by remember { mutableStateOf(false) }
    val activeScheduleResult = if (isPreviewNextSemester) nextScheduleResult else scheduleResult
    val schedule = activeScheduleResult?.getOrNull() ?: emptyList()
    val context = LocalContext.current

    LaunchedEffect(isActive) {
        if (isActive) scheduleViewModel.onScheduleEntered()
    }

    // 首次组合即渲染卡片，随页面转场正常淡入；
    // 仅数据变更时才走"清空一帧再重渲染"防闪烁，避免转场中段卡片整体弹入
    LaunchedEffect(schedule, isOverviewSchedule) {
        if (hasRenderedCards) {
            renderCourseCards = false
            withFrameNanos { }
            delay(48L)
        }
        renderCourseCards = true
        hasRenderedCards = true
    }

    LaunchedEffect(currentWeek) {
        state.animateScrollToItem(
            (currentWeek - 3).coerceAtLeast(0)
        )
    }

    LaunchedEffect(scheduleConfig?.week, isPreviewNextSemester) {
        if (!isPreviewNextSemester) {
            scheduleConfig?.week?.let { resolvedWeek ->
                currentWeek = resolvedWeek
                val targetPage = (resolvedWeek - 1).coerceIn(0, 19)
                if (pagerState.currentPage != targetPage) {
                    pagerState.scrollToPage(targetPage)
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        currentWeek = pagerState.currentPage + 1
    }

    LaunchedEffect(scheduleResult) {
        scheduleResult?.exceptionOrNull()?.let {
            Toast.makeText(context, "加载课表失败: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(nextScheduleResult) {
        nextScheduleResult?.exceptionOrNull()?.let {
            Toast.makeText(context, "加载下学期课表失败: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(activeScheduleResult, isPreviewNextSemester) {
        when {
            activeScheduleResult == null -> Unit
            activeScheduleResult.isFailure -> behaviorRuntime.onContentStateChanged(
                SemanticDomain.SCHEDULE,
                ContentStateBucket.ERROR,
                freshnessBucket = 7,
                resultCount = ResultCountBucket.ZERO,
                errorType = ErrorTypeBucket.NETWORK
            )
            else -> behaviorRuntime.onContentStateChanged(
                SemanticDomain.SCHEDULE,
                if (schedule.isEmpty()) ContentStateBucket.EMPTY else ContentStateBucket.READY,
                freshnessBucket = 1,
                resultCount = scheduleResultBucket(schedule.size)
            )
        }
    }

    LaunchedEffect(isPreviewNextSemester) {
        if (isPreviewNextSemester && nextScheduleResult == null) {
            scheduleViewModel.refreshNextSchedule()
        }
        val targetWeek = if (isPreviewNextSemester) 1 else scheduleConfig?.week ?: 1
        currentWeek = targetWeek
        pagerState.animateScrollToPage((targetWeek - 1).coerceAtLeast(0))
    }

    val radiant = isRadiantUi
    val baseColor = 50.a1.toSrgb().toHct()
    val macaronPalette = remember {
        listOf(
            Color(0xFF82ADF7), Color(0xFF7AE3D2), Color(0xFF77B6EF),
            Color(0xFFE19BB0), Color(0xFFE38874), Color(0xFF679ACD),
            Color(0xFFE87897), Color(0xFFEBB877), Color(0xFFC8A2C8),
            Color(0xFFA8E4A0), Color(0xFFFF8A80)
        )
    }
    val courseColors = remember(schedule, radiant) {
        val courseNames = schedule.asSequence().map { it.name }.distinct().toList()
        if (radiant) {
            courseNames.mapIndexed { index, name ->
                val paletteIndex = if (index < macaronPalette.size) {
                    index
                } else {
                    (name?.hashCode() ?: 0).mod(macaronPalette.size)
                }
                name to macaronPalette[paletteIndex]
            }.toMap()
        } else {
            courseNames.mapIndexed { index, name ->
                name to baseColor.copy(
                    h = 360.0 * index / courseNames.size.coerceAtLeast(1)
                ).toSrgb().toColor()
            }.toMap()
        }
    }
    val coursesByWeek = remember(schedule) {
        List(20) { pageIndex ->
            val week = pageIndex + 1
            schedule.filter { week in it.weekIndexes }
        }
    }
    val overviewCourseGroups = remember(schedule) {
        schedule
            .groupBy { Triple(it.weekday, it.startTime, it.length) }
            .values
            .toList()
    }
    val weekDateLabels = remember(scheduleConfig?.startTime) {
        val fallbackStart = requireNotNull(
            SimpleDateFormat("MM-dd", Locale.CHINA).parse("09-01")
        )
        val startTime = scheduleConfig?.startTime ?: fallbackStart
        val formatter = SimpleDateFormat("MM-dd", Locale.CHINA)
        List(20) { pageIndex ->
            List(7) { dayIndex ->
                Calendar.getInstance().apply {
                    time = startTime
                    add(Calendar.DATE, pageIndex * 7 + dayIndex)
                }.let { formatter.format(it.time) }
            }
        }
    }

    var detailedCourse by rememberSaveable { mutableStateOf<Course?>(null) }
    val settingsCardColor = 100.n1 withNight 20.n1

    @Composable
    fun ScheduleHeaderRow() {
        Row(
            modifier = if (radiant) {
                Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
            } else {
                Modifier.padding(end = 8.dp)
            },
        ) {
            // week selector
            LazyRow(
                modifier = Modifier.weight(1f),
                state = state,
                contentPadding = if (radiant) {
                    PaddingValues(0.dp)
                } else {
                    PaddingValues(horizontal = 16.dp)
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(20) {
                    val week = it + 1
                    val isSelected = week == currentWeek
                    CompositionLocalProvider(
                        LocalIndication provides ripple(
                            color = if (isSelected) {
                                100.n1 withNight 0.n1
                            } else {
                                0.n1 withNight 100.n1
                            }
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(ContinuousCapsule)
                                .background(
                                    animateColorAsState(
                                        targetValue = if (isSelected) {
                                            40.a1 withNight 90.a1
                                        } else {
                                            Color.Transparent
                                        }
                                    ).value
                                )
                                .clickable {
                                    if (currentWeek != week) {
                                        behaviorRuntime.recordCommittedMutationAsync(
                                            MutationId.SCHEDULE_WEEK_CHANGED,
                                            currentWeek,
                                            week,
                                            coarseValueBucket = if (week == scheduleConfig?.week) "CURRENT_WEEK" else "OTHER_WEEK"
                                        )
                                    }
                                    scope.launch {
                                        pagerState.animateScrollToPage(week - 1)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = week.toString(),
                                color = animateColorAsState(
                                    targetValue = if (isSelected) {
                                        100.n1 withNight 0.n1
                                    } else {
                                        0.n1 withNight 100.n1
                                    }
                                ).value,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
            // actions
            Row(
                modifier = (if (radiant) {
                    Modifier
                        .clip(ContinuousCapsule)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                } else {
                    Modifier.appLiquidGlassSurface(
                        shape = ContinuousCapsule,
                        fallbackColor = 100.n1 withNight 30.n1,
                        level = LiquidGlassSurfaceLevel.Floating
                    )
                }).padding(horizontal = 2.dp, vertical = 2.dp)
            ) {

                IconButton(
                    modifier = Modifier.size(if (radiant) 38.dp else 48.dp),
                    onClick = {
                        if (isPreviewNextSemester) {
                            behaviorRuntime.recordCommittedMutationAsync(
                                MutationId.SCHEDULE_SEMESTER_PREVIEW_CHANGED,
                                true,
                                false,
                                coarseValueBucket = "CURRENT_SEMESTER"
                            )
                            isPreviewNextSemester = false
                        }
                        scope.launch {
                            state.animateScrollToItem((currentWeek - 3).coerceAtLeast(0))
                        }
                        scope.launch {
                            pagerState.animateScrollToPage((scheduleConfig?.week ?: 1) - 1)
                        }
                    }
                ) {
                    if (radiant) {
                        Icon(
                            painter = painterResource(R.drawable.ic_aiming),
                            contentDescription = "回到本周",
                            modifier = Modifier.size(17.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "回到本周",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    modifier = Modifier.size(if (radiant) 38.dp else 48.dp),
                    onClick = { isSettingsVisible = true }
                ) {
                    if (radiant) {
                        Icon(
                            painter = painterResource(R.drawable.ic_config),
                            contentDescription = "课表设置",
                            modifier = Modifier.size(17.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "课表设置",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    modifier = Modifier.size(if (radiant) 38.dp else 48.dp),
                    onClick = {
                        if (isPreviewNextSemester) {
                            scheduleViewModel.refreshNextSchedule(true)
                        } else {
                            behaviorReporter.organic(AppActionId.MANUAL_REFRESH_SCHEDULE)
                            scheduleViewModel.refreshSchedule(true)
                        }
                    }
                ) {
                    if (radiant) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = "刷新课表",
                            modifier = Modifier.size(17.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新课表",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ScheduleGrid() {
        // schedule
        val cellWidth = (
                LocalConfiguration.current.screenWidthDp.dp -
                        (if (radiant) 12.dp else 0.dp) -
                        CourseCardSpec.mainColumnWidth -
                        CourseCardSpec.cellSpacing * 9
                ) / 7
        val cellHeight = 48.dp
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val pageWeek = page + 1
            val gridShape = SmoothRoundedCornerShape(32.dp)
            val gridSurface = if (radiant) {
                // 阴影由 liquidGlassSurface 内部的 Shadow(radius=14dp, alpha=0.12) 提供，
                // 勿再叠 Modifier.shadow——双层叠加会产生又黑又重的重影
                Modifier.liquidGlassSurface(
                    backdrop = LocalLiquidGlassAmbientBackdrop.current,
                    shape = gridShape,
                    surfaceColor = liquidGlassTint()
                )
            } else {
                Modifier.appLiquidGlassSurface(
                    shape = gridShape,
                    fallbackColor = 99.n1 withNight 20.n1
                )
            }
            Box(
                modifier = with(CourseCardSpec) {
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (radiant) Modifier.padding(horizontal = 6.dp) else Modifier
                        )
                        .height(mainRowHeight + (cellHeight + cellSpacing) * 13 + 24.dp)
                        .then(gridSurface)
                        .padding(top = 8.dp)
                        .padding(cellSpacing)
                }
            ) {
                // TODO: current time indicator
                // weekday tags

                val weekDates = weekDateLabels.getOrElse(page) { emptyList() }

                ScheduleGridLabels(
                    weekDates = weekDates,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    pageWeek = pageWeek,
                    currentWeek = scheduleConfig?.week,
                    currentWeekday = currentWeekday,
                    isInSemester = scheduleConfig?.isInSemester == true,
                    isPreviewNextSemester = isPreviewNextSemester
                )
                // courses
                if (!renderCourseCards) {
                    Unit
                } else if (isOverviewSchedule) {
                    overviewCourseGroups.forEach { sameTimeCourses ->
                        key(sameTimeCourses.joinToString("-") { it.hashCode().toString() }) {
                            OverviewCourseGroupCard(
                                courses = sameTimeCourses,
                                colors = courseColors,
                                cellWidth = cellWidth,
                                cellHeight = cellHeight,
                                currentWeek = pageWeek,
                                onClick = {
                                    behaviorReporter.organic(AppActionId.OPEN_COURSE_DETAIL)
                                    detailedCourse = it
                                }
                            )
                        }
                    }
                } else {
                    coursesByWeek.getOrElse(page) { emptyList() }.forEach { course ->
                        key(course.hashCode()) {
                            CourseCard(
                                course = course,
                                color = courseColors.getOrElse(course.name) { 50.a1 },
                                cellWidth = cellWidth,
                                cellHeight = cellHeight,
                                isCurrentWeek = true,
                                date = weekDates.getOrNull(course.weekday - 1),
                                onClick = {
                                    behaviorReporter.organic(AppActionId.OPEN_COURSE_DETAIL)
                                    detailedCourse = it
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ScheduleFreshnessLabel() {
        if (isPreviewNextSemester) return
        val fetchedText = remember(scheduleFetchedAt) {
            scheduleFetchedAt?.let {
                SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(it))
            }
        }
        val text = when {
            isScheduleRefreshing && fetchedText == null -> "正在获取最新课表…"
            isScheduleRefreshing -> "正在检查更新 · 上次获取 $fetchedText"
            scheduleRefreshError != null && fetchedText != null -> "更新失败 · 上次获取 $fetchedText"
            scheduleRefreshError != null -> "最新课表获取失败"
            fetchedText != null -> "课表获取于 $fetchedText"
            else -> "尚未从教务系统获取课表"
        }
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = 50.n1 withNight 70.n1,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }

    if (isSettingsVisible) {
        ScheduleSettingsDialog(
            isOverviewSchedule = isOverviewSchedule,
            isPreviewNextSemester = isPreviewNextSemester,
            backdropColor = settingsCardColor,
            onOverviewChange = { enabled ->
                val oldValue = isOverviewSchedule
                isOverviewSchedule = enabled
                behaviorRuntime.recordCommittedMutationAsync(
                    MutationId.SCHEDULE_OVERVIEW_CHANGED,
                    oldValue,
                    enabled
                )
            },
            onPreviewNextSemesterChange = { enabled ->
                val oldValue = isPreviewNextSemester
                isPreviewNextSemester = enabled
                behaviorRuntime.recordCommittedMutationAsync(
                    MutationId.SCHEDULE_SEMESTER_PREVIEW_CHANGED,
                    oldValue,
                    enabled,
                    coarseValueBucket = if (enabled) "NEXT_SEMESTER" else "CURRENT_SEMESTER"
                )
            },
            onDismiss = { isSettingsVisible = false }
        )
    }
    // course dialog
    detailedCourse?.let {
        CourseDetailDialog(
            course = it,
            onDismiss = { detailedCourse = null }
        )
    }

    GlassBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        CompositionLocalProvider(
            LocalLiquidGlassAmbientBackdrop provides if (radiant) {
                backdrop
            } else {
                LocalLiquidGlassAmbientBackdrop.current
            }
        ) {
            if (radiant) {
                val headerBg = if (LocalIsLiquidGlassEnabled.current) {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                } else {
                    96.n1 withNight 10.n1
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    // 固定标题栏（渐变遮罩层）：与内容区为兄弟叠加关系，zIndex 盖在可穿透内容之上
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            .zIndex(20f)
                    ) {
                        ScheduleHeaderRow()
                    }
                    // 可穿透滚动内容区——上穿渐变标题栏、下穿底部导航
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(bottom = 96.dp)
                    ) {
                        Spacer(modifier = Modifier.height(102.dp))
                        ScheduleGrid()
                        ScheduleFreshnessLabel()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .appLiquidGlassSceneBackground(96.n1 withNight 10.n1)
                        .verticalScroll(rememberScrollState())
                        .systemBarsPadding()
                        .padding(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScheduleHeaderRow()
                    ScheduleGrid()
                    ScheduleFreshnessLabel()
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ScheduleGridLabels(
    weekDates: List<String>,
    cellWidth: Dp,
    cellHeight: Dp,
    pageWeek: Int,
    currentWeek: Int?,
    currentWeekday: Int,
    isInSemester: Boolean,
    isPreviewNextSemester: Boolean
) {
    val radiant = isRadiantUi
    val textMeasurer = rememberTextMeasurer()
    val contentColor = LocalContentColor.current
    val secondaryColor = 50.n1 withNight 80.n1
    val selectedBackground = 90.a1
    val selectedContent = 0.n1
    val dayStyle = MaterialTheme.typography.labelLarge
    val secondaryStyle = MaterialTheme.typography.labelSmall
    val dayNames = remember(radiant) {
        if (radiant) {
            listOf("一", "二", "三", "四", "五", "六", "日")
        } else {
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        }
    }
    val timeLabels = remember {
        ScheduleViewModel.timetable.map { (index, time) ->
            index.toString() to time.substringBefore("-")
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        fun drawCentered(text: String, style: TextStyle, center: Offset) {
            val result = textMeasurer.measure(text = text, style = style)
            drawText(
                textLayoutResult = result,
                topLeft = Offset(
                    x = center.x - result.size.width / 2f,
                    y = center.y - result.size.height / 2f
                )
            )
        }

        val cellWidthPx = cellWidth.toPx()
        val cellHeightPx = cellHeight.toPx()
        val mainColumnWidthPx = CourseCardSpec.mainColumnWidth.toPx()
        val mainRowHeightPx = CourseCardSpec.mainRowHeight.toPx()
        val spacingPx = CourseCardSpec.cellSpacing.toPx()
        val cornerRadius = CornerRadius(8.dp.toPx())

        if (radiant && weekDates.isNotEmpty()) {
            val month = weekDates.first().substringBefore("-").trimStart('0')
            drawCentered(
                text = month,
                style = dayStyle.copy(color = secondaryColor),
                center = Offset(mainColumnWidthPx / 2f, mainRowHeightPx * 0.34f)
            )
            drawCentered(
                text = "月",
                style = secondaryStyle.copy(color = secondaryColor),
                center = Offset(mainColumnWidthPx / 2f, mainRowHeightPx * 0.70f)
            )
        }

        weekDates.forEachIndexed { index, date ->
            val left = mainColumnWidthPx + (cellWidthPx + spacingPx) * index + spacingPx
            val isCurrentWeekday = !isPreviewNextSemester &&
                isInSemester &&
                pageWeek == currentWeek &&
                index + 1 == currentWeekday
            if (isCurrentWeekday) {
                drawRoundRect(
                    color = selectedBackground,
                    topLeft = Offset(left, 0f),
                    size = Size(cellWidthPx, mainRowHeightPx),
                    cornerRadius = cornerRadius
                )
            }
            val color = if (isCurrentWeekday) selectedContent else contentColor
            val dateColor = if (isCurrentWeekday) selectedContent else secondaryColor
            val centerX = left + cellWidthPx / 2f
            drawCentered(
                text = dayNames.getOrElse(index) { "" },
                style = dayStyle.copy(color = color),
                center = Offset(centerX, mainRowHeightPx * 0.34f)
            )
            drawCentered(
                text = if (radiant) date.substringAfter("-").trimStart('0') else date,
                style = secondaryStyle.copy(color = dateColor),
                center = Offset(centerX, mainRowHeightPx * 0.70f)
            )
        }

        timeLabels.forEachIndexed { itemIndex, (section, time) ->
            val top = mainRowHeightPx + (cellHeightPx + spacingPx) * itemIndex + spacingPx
            val centerX = mainColumnWidthPx / 2f
            drawCentered(
                text = section,
                style = dayStyle.copy(color = contentColor),
                center = Offset(centerX, top + cellHeightPx * 0.34f)
            )
            drawCentered(
                text = time,
                style = (if (radiant) TextStyle(fontSize = 9.sp) else secondaryStyle)
                    .copy(color = secondaryColor),
                center = Offset(centerX, top + cellHeightPx * 0.70f)
            )
        }
    }

    // Keep Canvas drawing inexpensive while exposing individually discoverable labels at the
    // same positions as the visual day headers and time-axis cells.
    weekDates.forEachIndexed { index, date ->
        Box(
            modifier = Modifier
                .offset(x = CourseCardSpec.mainColumnWidth + (cellWidth + cellSpacing) * index + cellSpacing)
                .size(cellWidth, CourseCardSpec.mainRowHeight)
                .semantics {
                    contentDescription = scheduleDayDescription(index + 1, date)
                    heading()
                }
        )
    }
    ScheduleViewModel.timetable.entries.forEachIndexed { index, (section, time) ->
        Box(
            modifier = Modifier
                .offset(y = CourseCardSpec.mainRowHeight + (cellHeight + cellSpacing) * index + cellSpacing)
                .size(CourseCardSpec.mainColumnWidth, cellHeight)
                .semantics {
                    contentDescription = schedulePeriodDescription(section, time)
                }
        )
    }
}

private fun scheduleResultBucket(count: Int): ResultCountBucket = when (count) {
    0 -> ResultCountBucket.ZERO
    in 1..5 -> ResultCountBucket.ONE_TO_FIVE
    in 6..20 -> ResultCountBucket.SIX_TO_TWENTY
    else -> ResultCountBucket.TWENTY_ONE_PLUS
}

@Composable
private fun ScheduleSettingsDialog(
        isOverviewSchedule: Boolean,
        isPreviewNextSemester: Boolean,
        backdropColor: Color,
        onOverviewChange: (Boolean) -> Unit,
        onPreviewNextSemesterChange: (Boolean) -> Unit,
        onDismiss: () -> Unit
    ) {
        val dialogShape = SmoothRoundedCornerShape(28.dp)
        AlertDialog(
            modifier = Modifier.appLiquidGlassSurface(
                shape = dialogShape,
                fallbackColor = backdropColor,
                level = LiquidGlassSurfaceLevel.Floating,
                backdropSamplingEnabled = false
            ),
            shape = dialogShape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "课表设置",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScheduleSettingRow(
                        title = "总览课表",
                        description = "显示全部周次的课程，重叠课程会平分同一块时间区域",
                        selected = isOverviewSchedule,
                        onSelect = onOverviewChange
                    )
                    ScheduleSettingRow(
                        title = "预览下学期课表",
                        description = "切换到教务系统中的下学期课表",
                        selected = isPreviewNextSemester,
                        onSelect = onPreviewNextSemesterChange
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "完成",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }


    @Composable
    private fun ScheduleSettingRow(
        title: String,
        description: String,
        selected: Boolean,
        onSelect: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SmoothRoundedCornerShape(12.dp))
                .clickable { onSelect(!selected) }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AppToggle(
                checked = selected,
                onCheckedChange = onSelect,
                modifier = Modifier.padding(start = 16.dp),
                contentDescription = title
            )
        }
    }

@Composable
private fun OverviewCourseGroupCard(
    courses: List<Course>,
    colors: Map<String, Color>,
    cellWidth: Dp,
    cellHeight: Dp,
    currentWeek: Int,
    onClick: (Course) -> Unit
) {
    val course = courses.firstOrNull() ?: return
    val fullHeight = cellHeight * course.length + cellSpacing * (course.length - 1)
    val sortedCourses = remember(courses) {
        courses.sortedWith(
            compareBy<Course> { it.startWeek }
                .thenBy { it.endWeek }
                .thenBy { it.name ?: "" }
        )
    }
    Box(
        modifier = with(CourseCardSpec) {
            Modifier
                .size(
                    cellWidth,
                    fullHeight
                )
                .offset(
                    x = mainColumnWidth +
                                (cellWidth + cellSpacing) * (course.weekday - 1) +
                                cellSpacing,
                    y = mainRowHeight +
                            (cellHeight + cellSpacing) * (course.startTime - 1) +
                                cellSpacing
                )
                .clip(SmoothRoundedCornerShape(8.dp))
                .background(95.a1 withNight 30.n1)
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            sortedCourses.forEachIndexed { index, item ->
                val isCurrentWeek = currentWeek in item.weekIndexes
                val color = colors.getOrElse(item.name) { 50.a1 }
                val tonalPalettes = remember(color) {
                    courseTonalPalettes(color)
                }
                CompositionLocalProvider(
                    LocalTonalPalettes provides tonalPalettes
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(if (isCurrentWeek) color else color.copy(alpha = 0.45f))
                            .clickable { onClick(item) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = courseScheduleDescription(
                                    item,
                                    ScheduleViewModel.timetable,
                                    includeWeeks = true
                                )
                            }
                            .padding(4.dp)
                    ) {
                        OverviewCourseContent(
                            course = item,
                            stackedCount = sortedCourses.size,
                            slotHeightDp = fullHeight / sortedCourses.size
                        )
                    }
                }
                if (index != sortedCourses.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(100.n1.copy(alpha = 0.65f) withNight 0.n1.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.OverviewCourseContent(
    course: Course,
    stackedCount: Int,
    slotHeightDp: Dp
) {
    if (isRadiantUi) {
        RadiantOverviewCourseContent(course, stackedCount, slotHeightDp)
        return
    }
    Text(
        text = course.name ?: "",
        modifier = Modifier.padding(bottom = 38.dp),
        color = 100.n1,
        fontWeight = FontWeight.Bold,
        maxLines = if (stackedCount <= 1) 3 else 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelMedium
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = course.weekRangeText(),
            color = 100.n1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
        OverviewLocationPill(
            text = course.location.shortScheduleLocation(),
            maxLines = if (stackedCount <= 1) 2 else 1
        )
    }
}

@Composable
private fun BoxScope.RadiantOverviewCourseContent(
    course: Course,
    stackedCount: Int,
    slotHeightDp: Dp
) {
    val locationText = course.location.shortScheduleLocation()
    val weekText = course.weekRangeText()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val maxLines = remember(
        course.name,
        locationText,
        weekText,
        stackedCount,
        slotHeightDp,
        density
    ) {
        val pillHeight = with(density) {
            textMeasurer.measure(
                text = AnnotatedString(locationText),
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
            ).size.height.toDp() + 8.dp
        }
        val weekHeight = with(density) {
            textMeasurer.measure(
                text = AnnotatedString(weekText),
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
            ).size.height.toDp()
        }
        val nameLineHeight = with(density) {
            textMeasurer.measure(
                text = AnnotatedString("测试"),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
            ).size.height.toDp()
        }
        val usableHeight = slotHeightDp - 8.dp - weekHeight - pillHeight - 4.dp
        (usableHeight / nameLineHeight).toInt().coerceIn(1, 8)
    }

    Text(
        text = course.name ?: "",
        modifier = Modifier
            .fillMaxSize()
            .wrapContentHeight(Alignment.Top)
            .padding(bottom = 42.dp),
        color = 100.n1,
        fontWeight = FontWeight.Bold,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = 12.sp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = weekText,
            color = 100.n1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
        OverviewLocationPill(
            text = locationText,
            maxLines = if (stackedCount <= 1) 2 else 1
        )
    }
}

@Composable
private fun OverviewLocationPill(
    text: String,
    maxLines: Int
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(6.dp))
            .background(95.a1 withNight 30.n2)
            .padding(2.dp),
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        style = TextStyle(
            fontSize = if (isRadiantUi) 9.sp else 11.sp,
            color = 10.n1 withNight 90.n1,
            fontWeight = FontWeight.Bold
        )
    )
}
