package com.ahu.ahutong.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ahu.ahutong.assistant.AssistantAccessState
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.components.GuidedTourDialogOverlayState
import com.ahu.ahutong.ui.components.GuidedTourOverlay
import com.ahu.ahutong.ui.components.GuidedTourStep
import com.ahu.ahutong.ui.components.TourBounds
import com.ahu.ahutong.ui.screen.setup.OnboardingState
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongDockState
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongTourState
import android.content.Context

private enum class TourStopPage { HOME, SCHEDULE, XUEXIAOTONG, CURRENT }

private enum class TourGate { NONE, DOCK_TOGGLE, WORK_DETAIL_OPEN }

private enum class TourHostWindow { MAIN, WORK_DIALOG }

private data class TourStop(
    val targetId: String,
    val title: String,
    val body: String,
    val page: TourStopPage,
    val gate: TourGate = TourGate.NONE,
    val targetClickable: Boolean = false,
    val hostWindow: TourHostWindow = TourHostWindow.MAIN
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
        body = "扫一扫：支持扫描**美团单车**和**学习通签到**QR，一步跳转。\n笔记相机：智能识别在上的课程，归档笔记。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "home_hero",
        title = "任务速览",
        body = "提醒即将开始的考试和课程、**即将截止的作业**。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "home_campus",
        title = "校园卡",
        body = "点击即展开**付款二维码**。",
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
        body = "可以在此设置是否**显示周末**以及**自定义课程**。",
        page = TourStopPage.SCHEDULE
    ),
    TourStop(
        targetId = "nav_xuexiaotong",
        title = "日程与课程",
        body = "**学习通作业**按日历排列展示、学习通**课程任务点**完成情况。重复点击以切换两个子页。",
        page = TourStopPage.CURRENT,
        gate = TourGate.DOCK_TOGGLE
    ),
    TourStop(
        targetId = "tour_work_card",
        title = "作业详情",
        body = "点开一条作业查看状态和起止时间；正式作业还能跳转学习通或查看题目。",
        page = TourStopPage.XUEXIAOTONG,
        gate = TourGate.WORK_DETAIL_OPEN,
        targetClickable = true
    ),
    TourStop(
        targetId = "work_jump_btn",
        title = "跳转学习通",
        body = "**跳转到学习通**打开该作业，可以快捷提交。",
        page = TourStopPage.XUEXIAOTONG,
        hostWindow = TourHostWindow.WORK_DIALOG
    ),
    TourStop(
        targetId = "work_questions_btn",
        title = "查看题目",
        body = "**只读查看题目**，适合快速阅览作业量。",
        page = TourStopPage.XUEXIAOTONG,
        hostWindow = TourHostWindow.WORK_DIALOG
    ),
    TourStop(
        targetId = "xuexiaotong_refresh",
        title = "刷新日程与课程",
        body = "由于风控策略，作业与课程同步需要手动刷新，默认只抓取未完成的作业，您可以在日程设置内打开**全量抓取**。",
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
    onOpenXuexiaotongSchedule: () -> Unit,
    onDialogOverlayChanged: (GuidedTourDialogOverlayState?) -> Unit
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
        XuexiaotongTourState.closeWorkDetailRequested = true
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
            // 停在上一步的页面（如课表），不必先切回主页
            TourStopPage.CURRENT, null -> Unit
        }
    }
    LaunchedEffect(visible, index, XuexiaotongDockState.tab) {
        val gated = stops.getOrNull(index)?.gate == TourGate.DOCK_TOGGLE
        if (visible && gated && XuexiaotongDockState.tab != dockBaseline) {
            dockUnlocked = true
        }
    }

    var previousHostWindow by remember { mutableStateOf<TourHostWindow?>(null) }
    var detailOpenedThisStep by remember { mutableStateOf(false) }
    var detailMustCloseFirst by remember { mutableStateOf(false) }
    LaunchedEffect(visible, index) {
        if (!visible) {
            previousHostWindow = null
            detailOpenedThisStep = false
            return@LaunchedEffect
        }
        val stop = stops.getOrNull(index)
        val hostWindow = stop?.hostWindow
        if (previousHostWindow == TourHostWindow.WORK_DIALOG &&
            hostWindow != TourHostWindow.WORK_DIALOG
        ) {
            XuexiaotongTourState.closeWorkDetailRequested = true
        }
        previousHostWindow = hostWindow
        detailOpenedThisStep = false
        detailMustCloseFirst = stop?.gate == TourGate.WORK_DETAIL_OPEN &&
            XuexiaotongTourState.workDetailOpen
        // 回到弹窗内的步骤（如 11 → 10）时，若弹窗已关需要重新打开
        if (hostWindow == TourHostWindow.WORK_DIALOG && !XuexiaotongTourState.workDetailOpen) {
            XuexiaotongTourState.openWorkDetailRequested = true
        }
    }
    // 打开作业详情后自动进入弹窗内的两步；只认本步内新打开的事件，避免旧状态立刻顶回去
    LaunchedEffect(visible, index, XuexiaotongTourState.workDetailOpen) {
        val stop = stops.getOrNull(index) ?: return@LaunchedEffect
        if (!visible || stop.gate != TourGate.WORK_DETAIL_OPEN) return@LaunchedEffect
        if (!XuexiaotongTourState.workDetailOpen) {
            detailOpenedThisStep = false
            detailMustCloseFirst = false
            return@LaunchedEffect
        }
        if (detailMustCloseFirst) return@LaunchedEffect
        if (!detailOpenedThisStep) {
            detailOpenedThisStep = true
            if (index < stops.lastIndex) index += 1
        }
    }

    BackHandler(enabled = visible) {
        if (index > 0) index -= 1 else finish()
    }

    val current = stops.getOrNull(index)
    val dockGate = current?.gate == TourGate.DOCK_TOGGLE
    val gateSatisfied = when (current?.gate) {
        TourGate.DOCK_TOGGLE -> dockUnlocked
        TourGate.WORK_DETAIL_OPEN -> XuexiaotongTourState.workDetailOpen
        else -> true
    }
    val inDialog = current?.hostWindow == TourHostWindow.WORK_DIALOG
    val overlaySteps = stops.mapIndexed { i, stop ->
        GuidedTourStep(
            title = stop.title,
            body = stop.body,
            note = if (i == 0 && !assistantEnabled) "（该功能已关闭）" else null
        )
    }
    val targetBounds = current?.let { TourBounds.get(it.targetId) }
    val hint = when {
        current?.targetClickable == true -> "点击试试↑"
        dockGate -> "点两下试试→"
        else -> null
    }
    val hintBelow = current?.targetClickable == true

    fun advance() {
        if (index >= stops.lastIndex) finish() else index += 1
    }

    fun retreat() {
        if (index > 0) index -= 1 else finish()
    }

    val dialogOverlay = if (visible && inDialog) {
        GuidedTourDialogOverlayState(
            steps = overlaySteps,
            index = index,
            targetBounds = targetBounds,
            hint = hint,
            hintBelow = hintBelow,
            nextEnabled = gateSatisfied,
            onNext = {
                if (gateSatisfied) advance()
            },
            onPrevious = ::retreat,
            onSkip = ::finish
        )
    } else null
    SideEffect { onDialogOverlayChanged(dialogOverlay) }

    GuidedTourOverlay(
        visible = visible && !inDialog,
        steps = overlaySteps,
        index = index,
        targetBounds = targetBounds,
        allowHighlightedInteraction = current?.targetClickable == true || dockGate,
        nextEnabled = gateSatisfied,
        hint = hint,
        hintBelow = hintBelow,
        onNext = {
            if (gateSatisfied) advance()
        },
        onPrevious = ::retreat,
        onSkip = ::finish
    )
}
