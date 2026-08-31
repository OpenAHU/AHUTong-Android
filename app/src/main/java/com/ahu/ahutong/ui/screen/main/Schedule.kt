package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.R
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.screen.main.schedule.CourseCard
import com.ahu.ahutong.ui.screen.main.schedule.CourseCardSpec
import com.ahu.ahutong.ui.screen.main.schedule.CourseDetailDialog
import com.ahu.ahutong.ui.screen.main.schedule.shortScheduleLocation
import com.ahu.ahutong.ui.screen.main.schedule.weekRangeText
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.GlassBackdropContainer
import com.ahu.ahutong.ui.components.liquidGlassSurface
import com.ahu.ahutong.ui.components.liquidGlassTint
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.kyant.capsule.ContinuousCapsule
import com.kyant.monet.Hct.Companion.toHct
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.PaletteStyle
import com.kyant.monet.TonalPalettes.Companion.toTonalPalettes
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.n2
import com.kyant.monet.toColor
import com.kyant.monet.toSrgb
import com.kyant.monet.withNight
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
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
    behaviorRuntime: BehaviorPredictionRuntime
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
    var isPreviewNextSemester by rememberSaveable { mutableStateOf(false) }
    var isOverviewSchedule by rememberSaveable { mutableStateOf(false) }
    var isSettingsVisible by rememberSaveable { mutableStateOf(false) }
    val activeScheduleResult = if (isPreviewNextSemester) nextScheduleResult else scheduleResult
    val schedule = activeScheduleResult?.getOrNull() ?: emptyList()
    val context = LocalContext.current

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

    val baseColor = 50.a1.toSrgb().toHct()
    val radiant = isRadiantUi
    // Radiant：清新色库（用户指定的 11 个中等饱和度柔和色）
    val macaronPalette = remember {
        listOf(
            Color(0xFF82ADF7), Color(0xFF7AE3D2), Color(0xFF77B6EF), Color(0xFFE19BB0),
            Color(0xFFE38874), Color(0xFF679ACD), Color(0xFFE87897), Color(0xFFEBB877),
            Color(0xFFC8A2C8), Color(0xFFA8E4A0), Color(0xFFFF8A80)
        )
    }
    val courseColors by remember(schedule) {
        mutableStateOf(
            if (radiant) {
                // Radiant 分支：马卡龙库分配。课程数 ≤ 库大小时不重复（按库顺序取），
                // 超出库后按课程名哈希取色（允许撞色，同一课程名恒定同色）。
                val names = schedule.map { it.name }.distinct()
                names.mapIndexed { index, name ->
                    val color = if (index < macaronPalette.size) {
                        macaronPalette[index]
                    } else {
                        macaronPalette[((name?.hashCode() ?: 0).mod(macaronPalette.size))]
                    }
                    name to color
                }.toMap()
            } else {
                // 冻结分支：HCT 均匀色相旋转（原逻辑）
                schedule.map { it.name }.distinct()
                    .mapIndexed { index, name ->
                        name to baseColor.copy(
                            h = 360.0 * index / schedule.map { it.name }
                                .distinct().size.coerceAtLeast(1)
                        ).toSrgb()
                            .toColor()
                    }.toMap()
            }
        )
    }

    val currentWeekCourses = schedule

    var detailedCourse by rememberSaveable { mutableStateOf<Course?>(null) }
    val settingsCardColor = 100.n1 withNight 20.n1
    if (isRadiantUi) {
        // RadiantUI 课表：GlassBackdropContainer 提供玻璃采样层（液态玻璃开关下带渐变色带），
        // 固定标题栏（渐变遮罩）+ 网格卡（液态玻璃 + 阴影）复用主页校园卡同款材质。
        val headerBg = if (LocalIsLiquidGlassEnabled.current) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            96.n1 withNight 10.n1
        }
        GlassBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // week selector
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        state = state,
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
                                Text(
                                    text = week.toString(),
                                    modifier = Modifier
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
                                        }
                                        .padding(16.dp, 8.dp),
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
                    // actions
                    Row(
                        modifier = Modifier
                            .clip(ContinuousCapsule)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {

                        IconButton(
                            modifier = Modifier.size(38.dp),
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
                            Icon(
                                painter = painterResource(R.drawable.ic_aiming),
                                contentDescription = "回到本周",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(38.dp),
                            onClick = { isSettingsVisible = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_config),
                                contentDescription = "课表设置",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(38.dp),
                            onClick = {
                                if (isPreviewNextSemester) {
                                    scheduleViewModel.refreshNextSchedule(true)
                                } else {
                                    behaviorReporter.organic(AppActionId.MANUAL_REFRESH_SCHEDULE)
                                    scheduleViewModel.refreshSchedule(true)
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = "刷新课表",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    }
                }
                // schedule 网格：可穿透滚动内容区——上穿渐变标题栏、下穿底部导航。
                // 结构：Box 铺满整页（无外层留白），标题栏渐变层 zIndex 盖在其上；
                // 滚动 Column 自带顶部停靠占位（标题栏高度）与底部 nav 留白。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(102.dp))
                        val cellWidth = (
                                LocalConfiguration.current.screenWidthDp.dp -
                                        12.dp - // 网格卡左右各 6dp 内缩
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
                        Box(
                            modifier = with(CourseCardSpec) {
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .height(mainRowHeight + (cellHeight + cellSpacing) * 13 + 24.dp)
                                    .liquidGlassSurface(
                                        backdrop = backdrop,
                                        shape = gridShape,
                                        surfaceColor = liquidGlassTint()
                                    )
                                    .padding(top = 8.dp)
                                    .padding(cellSpacing)
                            }
                        ) {
                            // TODO: current time indicator
                            // weekday tags

                            val weekDates by remember(pageWeek, scheduleConfig?.startTime) {
                                mutableStateOf(
                                    List(7) { index ->
                                        Calendar.getInstance().apply {
                                            time = scheduleConfig?.startTime
                                                ?: SimpleDateFormat("MM-dd", Locale.CHINA).parse("09-01")
                                            add(Calendar.DATE, ((pageWeek - 1) * 7) + index)
                                        }
                                    }
                                )
                            }

                            // 左上空闲格：显示本页（按周一）所在月份，竖排（如 9\n月）
                            val monthNumber = SimpleDateFormat("M", Locale.CHINA)
                                .format(weekDates.first().time)
                            Column(
                                modifier = with(CourseCardSpec) {
                                    Modifier
                                        .size(mainColumnWidth, mainRowHeight)
                                        .clip(SmoothRoundedCornerShape(8.dp))
                                },
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = monthNumber,
                                    color = 50.n1 withNight 80.n1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "月",
                                    color = 50.n1 withNight 80.n1,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            weekDates.forEachIndexed { index, date ->
                                val isCurrentWeekday =
                                    !isPreviewNextSemester &&
                                        scheduleConfig?.isInSemester == true &&
                                        pageWeek == scheduleConfig?.week &&
                                        index + 1 == currentWeekday
                                Column(
                                    modifier = with(CourseCardSpec) {
                                        Modifier
                                            .size(cellWidth, mainRowHeight)
                                            .offset(
                                                x = mainColumnWidth + (cellWidth + cellSpacing) * index + cellSpacing
                                            )
                                            .clip(SmoothRoundedCornerShape(8.dp))
                                            .background(if (isCurrentWeekday) 90.a1 else Color.Unspecified)
                                    },
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = arrayOf(
                                            "一",
                                            "二",
                                            "三",
                                            "四",
                                            "五",
                                            "六",
                                            "日"
                                        )[index],
                                        color = if (isCurrentWeekday) 0.n1 else Color.Unspecified,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        // Radiant 密度优化：日期只显示"日"，不显示月
                                        text = SimpleDateFormat("d", Locale.CHINA).format(date.time),
                                        color = if (isCurrentWeekday) 0.n1 else 50.n1 withNight 80.n1,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            // time tags
                            // Radiant 密度优化：节次列显示节号 + 开始时间（9sp）
                            ScheduleViewModel.timetable.forEach { (index, time) ->
                                Column(
                                    modifier = with(CourseCardSpec) {
                                        Modifier
                                            .size(mainColumnWidth, cellHeight)
                                            .offset(
                                                y = mainRowHeight + (cellHeight + cellSpacing) * (index - 1) + cellSpacing
                                            )
                                            .clip(SmoothRoundedCornerShape(8.dp))
                                    },
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = index.toString(),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = time.substringBefore("-"),
                                        color = 50.n1 withNight 80.n1,
                                        style = TextStyle(fontSize = 9.sp)
                                    )
                                }
                            }
                            // courses
                            if (isOverviewSchedule) {
                                currentWeekCourses
                                    .groupBy { "${it.weekday}-${it.startTime}-${it.length}" }
                                    .values
                                    .forEach { sameTimeCourses ->
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
                                currentWeekCourses.forEach { course ->
                                    val isCurrentWeek = pageWeek in course.weekIndexes
                                    if (isCurrentWeek) {
                                        key(course.hashCode()) {

                                            CourseCard(
                                                course = course,
                                                color = courseColors.getOrElse(course.name) { 50.a1 },
                                                cellWidth = cellWidth,
                                                cellHeight = cellHeight,
                                                isCurrentWeek = isCurrentWeek,
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
                    // 底部留白：内容可滚入底部导航栏之下
                    Spacer(modifier = Modifier.height(108.dp))
                    }
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
            }
        }
    } else {
        // 冻结分支：原版课表，逐行保留，不再修改
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(end = 8.dp),
            ) {
                // week selector
                LazyRow(
                    modifier = Modifier.weight(1f),
                    state = state,
                    contentPadding = PaddingValues(horizontal = 16.dp),
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
                            Text(
                                text = week.toString(),
                                modifier = Modifier
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
                                    }
                                    .padding(16.dp, 8.dp),
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
                // actions
                Row(
                    modifier = Modifier
                        .clip(ContinuousCapsule)
                        .background(100.n1 withNight 30.n1)
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                ) {

                    IconButton(
                        modifier = Modifier.size(38.dp),
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
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(38.dp),
                        onClick = { isSettingsVisible = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        modifier = Modifier.size(38.dp),
                        onClick = {
                            if (isPreviewNextSemester) {
                                scheduleViewModel.refreshNextSchedule(true)
                            } else {
                                behaviorReporter.organic(AppActionId.MANUAL_REFRESH_SCHEDULE)
                                scheduleViewModel.refreshSchedule(true)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            // schedule
            val cellWidth = (
                    LocalConfiguration.current.screenWidthDp.dp -
                            CourseCardSpec.mainColumnWidth -
                            CourseCardSpec.cellSpacing * 9
                    ) / 7
            val cellHeight = 48.dp
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val pageWeek = page + 1
                Box(
                    modifier = with(CourseCardSpec) {
                        Modifier
                            .fillMaxWidth()
                            .height(mainRowHeight + (cellHeight + cellSpacing) * 13 + 24.dp)
                            .clip(SmoothRoundedCornerShape(32.dp))
                            .background(99.n1 withNight 20.n1)
                            .padding(top = 8.dp)
                            .padding(cellSpacing)
                    }
                ) {
                    // TODO: current time indicator
                    // weekday tags

                    val weekDates by remember(pageWeek, scheduleConfig?.startTime) {
                        mutableStateOf(
                            List(7) { index ->
                                Calendar.getInstance().apply {
                                    time = scheduleConfig?.startTime
                                        ?: SimpleDateFormat("MM-dd", Locale.CHINA).parse("09-01")
                                    add(Calendar.DATE, ((pageWeek - 1) * 7) + index)
                                }
                            }
                        )
                    }

                    weekDates.forEachIndexed { index, date ->
                        val isCurrentWeekday =
                            !isPreviewNextSemester &&
                                scheduleConfig?.isInSemester == true &&
                                pageWeek == scheduleConfig?.week &&
                                index + 1 == currentWeekday
                        Column(
                            modifier = with(CourseCardSpec) {
                                Modifier
                                    .size(cellWidth, mainRowHeight)
                                    .offset(
                                        x = mainColumnWidth + (cellWidth + cellSpacing) * index + cellSpacing
                                    )
                                    .clip(SmoothRoundedCornerShape(8.dp))
                                    .background(if (isCurrentWeekday) 90.a1 else Color.Unspecified)
                            },
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = arrayOf(
                                    "周一",
                                    "周二",
                                    "周三",
                                    "周四",
                                    "周五",
                                    "周六",
                                    "周日"
                                )[index],
                                color = if (isCurrentWeekday) 0.n1 else Color.Unspecified,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = SimpleDateFormat("MM-dd", Locale.CHINA).format(date.time),
                                color = if (isCurrentWeekday) 0.n1 else 50.n1 withNight 80.n1,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // time tags
                    ScheduleViewModel.timetable.forEach { (index, time) ->
                        Column(
                            modifier = with(CourseCardSpec) {
                                Modifier
                                    .size(mainColumnWidth, cellHeight)
                                    .offset(
                                        y = mainRowHeight + (cellHeight + cellSpacing) * (index - 1) + cellSpacing
                                    )
                                    .clip(SmoothRoundedCornerShape(8.dp))
                            },
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = index.toString(),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = time.substringBefore("-"),
                                color = 50.n1 withNight 80.n1,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    // courses
                    if (isOverviewSchedule) {
                        currentWeekCourses
                            .groupBy { "${it.weekday}-${it.startTime}-${it.length}" }
                            .values
                            .forEach { sameTimeCourses ->
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
                        currentWeekCourses.forEach { course ->
                            val isCurrentWeek = pageWeek in course.weekIndexes
                            if (isCurrentWeek) {
                                key(course.hashCode()) {

                                    CourseCard(
                                        course = course,
                                        color = courseColors.getOrElse(course.name) { 50.a1 },
                                        cellWidth = cellWidth,
                                        cellHeight = cellHeight,
                                        isCurrentWeek = isCurrentWeek,
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
        }
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
        AlertDialog(
            containerColor = backdropColor,
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "课表设置",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = 0.n1 withNight 100.n1
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
                        color = 40.a1 withNight 80.a1
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
                    color = 0.n1 withNight 100.n1
                )
                Text(
                    text = description,
                    color = 50.n1 withNight 80.n1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = selected,
                onCheckedChange = onSelect,
                modifier = Modifier.padding(start = 16.dp)
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
                CompositionLocalProvider(
                    LocalTonalPalettes provides color.toTonalPalettes(
                        style = PaletteStyle.Vibrant,
                        tonalValues = doubleArrayOf()
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(if (isCurrentWeek) color else color.copy(alpha = 0.45f))
                            .clickable { onClick(item) }
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
    } else {
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
}

@Composable
private fun BoxScope.RadiantOverviewCourseContent(
    course: Course,
    stackedCount: Int,
    slotHeightDp: Dp
) {
    // Radiant：与普通课程卡统一的字号/省略策略（12sp 课程名 + 9sp 底部文字 + 动态省略）
    val locationText = course.location.shortScheduleLocation()
    val weekText = course.weekRangeText()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val maxLines = remember(course.name, locationText, weekText, stackedCount, slotHeightDp, density) {
        val pillHeight = with(density) {
            textMeasurer.measure(
                text = AnnotatedString(locationText),
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
            ).size.height.toDp() + 2.dp * 2 + 2.dp * 2
        }
        val weekHeight = with(density) {
            textMeasurer.measure(
                text = AnnotatedString(weekText),
                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
            ).size.height.toDp()
        }
        val courseStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val oneLineHeight = with(density) {
            textMeasurer.measure(
                text = AnnotatedString("测试"),
                style = courseStyle
            ).size.height.toDp()
        }
        val slotUsable = slotHeightDp - 4.dp * 2 - weekHeight - pillHeight - 2.dp - 2.dp
        ((slotUsable / oneLineHeight).toInt()).coerceIn(1, 8)
    }

    Text(
        text = course.name ?: "",
        modifier = Modifier
            .fillMaxSize()
            .wrapContentHeight(Alignment.Top)
            .padding(bottom = 4.dp + 38.dp),
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
            style = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
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
