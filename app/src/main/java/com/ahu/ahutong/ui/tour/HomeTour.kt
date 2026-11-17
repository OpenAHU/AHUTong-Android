package com.ahu.ahutong.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ahu.ahutong.assistant.AssistantAccessState
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.components.GuidedTourOverlay
import com.ahu.ahutong.ui.components.GuidedTourStep
import com.ahu.ahutong.ui.components.TourBounds
import com.ahu.ahutong.ui.screen.setup.OnboardingState
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongDockState
import android.content.Context

private enum class TourStopPage { HOME, SCHEDULE, XUEXIAOTONG }

private enum class TourGate { NONE, DOCK_TOGGLE }

private data class TourStop(
    val targetId: String,
    val title: String,
    val body: String,
    val page: TourStopPage,
    val gate: TourGate = TourGate.NONE,
    val targetClickable: Boolean = false
)

private val TourStops = listOf(
    TourStop(
        targetId = "home_ai",
        title = "AHUTong Intelligence",
        body = "安大通原生智能体，通过自然语言把你的需求拆解并执行。目前已支持查询空闲教室、作业辅导等30+能力。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "home_camera",
        title = "智慧视觉",
        body = "扫一扫：支持扫描美团单车和学习通签到QR，一步跳转。\n笔记相机：智能识别在上的课程，归档笔记。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "home_hero",
        title = "任务速览",
        body = "提醒即将开始的考试和课程、即将截止的作业。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "home_campus",
        title = "校园卡",
        body = "点击即展开付款二维码。",
        page = TourStopPage.HOME,
        targetClickable = true
    ),
    TourStop(
        targetId = "home_widgets",
        title = "小工具",
        body = "长按以调整顺序、增删卡片，剩余小工具可以在更多中找到。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "schedule_settings",
        title = "课表设置",
        body = "可以在此设置是否显示周末以及自定义课程。",
        page = TourStopPage.SCHEDULE
    ),
    TourStop(
        targetId = "nav_xuexiaotong",
        title = "日程与课程",
        body = "学习通作业按日历排列展示、学习通课程任务点完成情况。重复点击以切换两个子页。",
        page = TourStopPage.HOME,
        gate = TourGate.DOCK_TOGGLE
    ),
    TourStop(
        targetId = "xuexiaotong_refresh",
        title = "刷新日程与课程",
        body = "由于风控策略，作业与课程同步需要手动刷新，默认只抓取未完成的作业，您可以在日程设置内打开全量抓取。",
        page = TourStopPage.XUEXIAOTONG
    )
)

internal object HomeTourState {
    private const val PREFS = "home_tour"
    private const val KEY_SEEN = "seen_v1"

    var replaySignal by mutableIntStateOf(0)
        private set

    /** 导览进行中：主页据此让出空间（如隐藏必吃榜确认卡）。 */
    var running by mutableStateOf(false)

    /** 离开校园卡步骤时通知主页把卡收起。 */
    var campusCollapseSignal by mutableIntStateOf(0)
        private set

    fun requestReplay() {
        replaySignal += 1
    }

    fun requestCampusCollapse() {
        campusCollapseSignal += 1
    }

    fun shouldAutoStart(context: Context): Boolean =
        !hasSeen(context) &&
            OnboardingState.isCompleted() &&
            SessionStore.isLoggedIn() &&
            AHUCache.isPrivacyAccepted()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hasSeen(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_SEEN, false) }.getOrDefault(false)

    fun markSeen(context: Context) {
        runCatching { prefs(context).edit().putBoolean(KEY_SEEN, true).apply() }
    }
}

@Composable
internal fun HomeTourHost(
    currentRoute: String?,
    undergraduateEnabled: Boolean,
    onOpenHome: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenXuexiaotongSchedule: () -> Unit
) {
    val context = LocalContext.current
    val stops = remember(undergraduateEnabled) {
        if (undergraduateEnabled) {
            TourStops
        } else {
            TourStops.filterNot { it.targetId == "schedule_settings" }
        }
    }
    var visible by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var handledReplay by remember { mutableIntStateOf(HomeTourState.replaySignal) }
    var previousIndex by remember { mutableIntStateOf(-1) }
    var dockBaseline by remember { mutableStateOf(XuexiaotongDockState.tab) }
    var dockUnlocked by remember { mutableStateOf(false) }
    val assistantEnabled = AssistantAccessState.enabled
    LaunchedEffect(Unit) { AssistantAccessState.ensureLoaded() }
    LaunchedEffect(visible) {
        if (visible) AssistantAccessState.refresh()
    }

    fun start() {
        HomeTourState.running = true
        index = 0
        visible = true
    }

    fun finish() {
        visible = false
        HomeTourState.running = false
        HomeTourState.markSeen(context)
    }

    LaunchedEffect(HomeTourState.replaySignal) {
        if (HomeTourState.replaySignal > handledReplay) {
            handledReplay = HomeTourState.replaySignal
            start()
        }
    }
    LaunchedEffect(currentRoute) {
        if (!visible && currentRoute == "home" && HomeTourState.shouldAutoStart(context)) {
            start()
        }
    }
    LaunchedEffect(visible, index) {
        if (!visible) return@LaunchedEffect
        val previous = previousIndex
        previousIndex = index
        if (previous >= 0 && previous != index &&
            stops.getOrNull(previous)?.targetId == "home_campus"
        ) {
            HomeTourState.requestCampusCollapse()
        }
        dockUnlocked = false
        dockBaseline = XuexiaotongDockState.tab
        when (stops.getOrNull(index)?.page) {
            TourStopPage.HOME -> onOpenHome()
            TourStopPage.SCHEDULE -> onOpenSchedule()
            TourStopPage.XUEXIAOTONG -> onOpenXuexiaotongSchedule()
            null -> Unit
        }
    }
    LaunchedEffect(visible, index, XuexiaotongDockState.tab) {
        val gated = stops.getOrNull(index)?.gate == TourGate.DOCK_TOGGLE
        if (visible && gated && XuexiaotongDockState.tab != dockBaseline) {
            dockUnlocked = true
        }
    }

    BackHandler(enabled = visible) {
        if (index > 0) index -= 1 else finish()
    }

    val current = stops.getOrNull(index)
    val gated = current?.gate == TourGate.DOCK_TOGGLE
    GuidedTourOverlay(
        visible = visible,
        steps = stops.mapIndexed { i, stop ->
            GuidedTourStep(
                title = stop.title,
                body = stop.body,
                note = if (i == 0 && !assistantEnabled) "（该功能已关闭）" else null
            )
        },
        index = index,
        targetBounds = current?.let { TourBounds.get(it.targetId) },
        allowTargetInteraction = gated,
        allowHighlightedInteraction = current?.targetClickable == true,
        nextEnabled = !gated || dockUnlocked,
        hint = if (gated) "点两下试试→" else null,
        onNext = {
            if (gated && !dockUnlocked) return@GuidedTourOverlay
            if (index >= stops.lastIndex) finish() else index += 1
        },
        onPrevious = {
            if (index > 0) index -= 1 else finish()
        },
        onSkip = ::finish
    )
}
