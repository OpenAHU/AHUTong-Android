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
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.components.GuidedTourOverlay
import com.ahu.ahutong.ui.components.GuidedTourStep
import com.ahu.ahutong.ui.components.TourBounds
import com.ahu.ahutong.ui.screen.setup.OnboardingState
import android.content.Context

private enum class TourStopPage { HOME, CUSTOM_COURSES, XUEXIAOTONG }

private data class TourStop(
    val targetId: String,
    val title: String,
    val body: String,
    val page: TourStopPage
)

private val TourStops = listOf(
    TourStop(
        targetId = "home_ai",
        title = "安大通 AI",
        body = "点一下唤起 AI，课表、空闲教室、成绩、校园通知都能直接问。还没配置模型时会先带你去 AI 设置。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "home_camera",
        title = "扫一扫与笔记",
        body = "扫共享单车和外卖平台的码，也能拍板书做笔记；笔记会按上课时间自动归到对应课程。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "home_hero",
        title = "今天最要紧的事",
        body = "正在上的课、下一场考试、最近的作业截止时间都显示在这里。作业上屏时，点一下可以直接进入日程。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "home_campus",
        title = "校园卡",
        body = "点一下展开付款码，食堂和超市都能直接刷；余额会随主页一起刷新。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "home_widgets",
        title = "常用小工具",
        body = "长按主页可以调整顺序、增删入口；“更多”里能添加校园通知、电费充值等。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "custom_courses_add",
        title = "自定义课程",
        body = "教务课表里没有的加课、晚自习可以手动添加；只保存在本机，按学期分开管理。",
        page = TourStopPage.CUSTOM_COURSES
    ),
    TourStop(
        targetId = "nav_xuexiaotong",
        title = "日程与课程",
        body = "点进来是学习通的日程页；已经在这个页面时，再点一下就在日程和课程之间切换。",
        page = TourStopPage.HOME
    ),
    TourStop(
        targetId = "xuexiaotong_refresh",
        title = "刷新当前页",
        body = "日程页刷新作业，课程页刷新学习进度；第一次连接学习通时会自动抓取本学期的全量作业。",
        page = TourStopPage.XUEXIAOTONG
    )
)

internal object HomeTourState {
    private const val PREFS = "home_tour"
    private const val KEY_SEEN = "seen_v1"

    var replaySignal by mutableIntStateOf(0)
        private set

    fun requestReplay() {
        replaySignal += 1
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
    onOpenCustomCourses: () -> Unit,
    onOpenXuexiaotongSchedule: () -> Unit
) {
    val context = LocalContext.current
    val stops = remember(undergraduateEnabled) {
        if (undergraduateEnabled) {
            TourStops
        } else {
            TourStops.filterNot { it.page == TourStopPage.CUSTOM_COURSES }
        }
    }
    var visible by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var handledReplay by remember { mutableIntStateOf(HomeTourState.replaySignal) }

    fun finish() {
        visible = false
        HomeTourState.markSeen(context)
    }

    LaunchedEffect(HomeTourState.replaySignal) {
        if (HomeTourState.replaySignal > handledReplay) {
            handledReplay = HomeTourState.replaySignal
            index = 0
            visible = true
        }
    }
    LaunchedEffect(currentRoute) {
        if (!visible && currentRoute == "home" && HomeTourState.shouldAutoStart(context)) {
            index = 0
            visible = true
        }
    }
    LaunchedEffect(visible, index) {
        if (!visible) return@LaunchedEffect
        when (stops.getOrNull(index)?.page) {
            TourStopPage.HOME -> onOpenHome()
            TourStopPage.CUSTOM_COURSES -> onOpenCustomCourses()
            TourStopPage.XUEXIAOTONG -> onOpenXuexiaotongSchedule()
            null -> Unit
        }
    }

    BackHandler(enabled = visible) {
        if (index > 0) index -= 1 else finish()
    }

    val current = stops.getOrNull(index)
    GuidedTourOverlay(
        visible = visible,
        steps = stops.map { GuidedTourStep(it.title, it.body) },
        index = index,
        targetBounds = current?.let { TourBounds.get(it.targetId) },
        onNext = {
            if (index >= stops.lastIndex) finish() else index += 1
        },
        onPrevious = {
            if (index > 0) index -= 1 else finish()
        },
        onSkip = ::finish
    )
}
