package com.ahu.ahutong.ui.screen.assistant

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.R
import com.ahu.ahutong.assistant.AssistantAccessState
import com.ahu.ahutong.assistant.AssistantCard
import com.ahu.ahutong.assistant.AssistantCardItem
import com.ahu.ahutong.assistant.AssistantCardTone
import com.ahu.ahutong.assistant.AssistantService
import com.ahu.ahutong.assistant.AssistantServiceEvent
import com.ahu.ahutong.assistant.AssistantTrust
import com.ahu.ahutong.assistant.safety.AssistantApprovalRequest
import com.ahu.ahutong.ui.components.headerBackdropSource
import com.ahu.ahutong.ui.components.headerScrim
import com.ahu.ahutong.ui.glow.AssistantGlowState
import com.ahu.ahutong.ui.state.ScheduleViewModel
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全屏 AI 问答页：流式文本 + Markdown/LaTeX + 可折叠工具卡 + 状态胶囊。
 * 交互与 NEXT 对话页一致；输入栏继续使用 AI 层自绘的发光胶囊。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantChatScreen(
    scheduleViewModel: ScheduleViewModel,
    onOpenRoute: (String) -> Unit,
) {
    val service = rememberAssistantService(scheduleViewModel)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var expandedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedRows by remember { mutableStateOf<Set<String>>(emptySet()) }

    val items = AssistantChatState.items
    val status = AssistantChatState.status
    val running = AssistantChatState.running
    val displayItems = buildChatDisplay(items)
    val roundEnds = roundEndTurnKeys(displayItems)
    val roundNotices = buildRoundNotices(items)
    // 用户刚发问、还没收到首词时，也要把上一轮的底栏保留下来。
    val activeUserId = displayItems.lastOrNull { it is ChatDisplay.User }?.key
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    // 底栏（输入框 + IME/系统栏占位）整体高度；列表尾部用同高占位。
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
    val streamingLength = (items.lastOrNull() as? AssistantChatItem.Assistant)?.text?.length ?: 0
    val currentTurnKey = displayItems.lastOrNull { it is ChatDisplay.Turn }?.key
    var previousBarHeight by remember { mutableStateOf(0.dp) }
    var previousTurnKey by remember { mutableStateOf<String?>(null) }
    var initialBarMeasured by remember { mutableStateOf(false) }

    // DS 的“接近底部”：距底部 300px 内都算在底部（约 100dp @3x）。
    val nearBottom by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            (last.offset + last.size) - info.viewportEndOffset <= 300f
        }
    }

    // 新消息 / 新卡片：用户原本在底部时贴底。
    LaunchedEffect(activeUserId) {
        if (activeUserId != null) listState.scrollToItem(Int.MAX_VALUE, 0)
    }
    LaunchedEffect(displayItems.size) {
        if (nearBottom) listState.scrollToItem(Int.MAX_VALUE, 0)
    }
    // 首次测到底栏高度后补一次贴底；之后的键盘/展开变化交给 DS 的三闸门。
    LaunchedEffect(bottomBarHeight) {
        if (!initialBarMeasured && bottomBarHeight > 0.dp) {
            initialBarMeasured = true
            if (nearBottom) listState.scrollToItem(Int.MAX_VALUE, 0)
        }
    }

    // DS 的贴底三闸门：底栏高度变大、仍在底部 300px 内、用户没在滚动、当前消息没切换。
    LaunchedEffect(bottomBarHeight, currentTurnKey) {
        val grew = bottomBarHeight > previousBarHeight
        val sameTurn = currentTurnKey == previousTurnKey
        if (grew && nearBottom && !listState.isScrollInProgress && sameTurn) {
            listState.scrollToItem(Int.MAX_VALUE, 0)
        }
        previousBarHeight = bottomBarHeight
        previousTurnKey = currentTurnKey
    }

    // 流式跟随：只跟一屏之内、用户仍在底部的回答；超过一屏就停止抢阅读位置。
    LaunchedEffect(streamingLength, currentTurnKey) {
        if (streamingLength == 0 || currentTurnKey == null || !nearBottom) return@LaunchedEffect
        if (listState.isScrollInProgress) return@LaunchedEffect
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentTurnKey }
            ?: return@LaunchedEffect
        val viewportHeight = listState.layoutInfo.viewportEndOffset -
            listState.layoutInfo.viewportStartOffset - bottomBarHeightPx
        if (viewportHeight <= 0 || info.size > viewportHeight) return@LaunchedEffect
        val usableBottom = listState.layoutInfo.viewportEndOffset - bottomBarHeightPx
        val distance = info.offset + info.size - usableBottom
        if (distance > 0) listState.scrollBy(distance.toFloat())
    }

    LaunchedEffect(AssistantChatState.pendingQuestion) {
        val question = AssistantChatState.consumePending() ?: return@LaunchedEffect
        AssistantChatState.attachJob(scope.launch { runStream(service, scope, question) })
    }

    LaunchedEffect(AssistantAccessState.enabled) {
        if (!AssistantAccessState.enabled) {
            AssistantChatState.close()
            AssistantGlowState.visible = false
        }
    }

    BackHandler(enabled = AssistantChatState.visible) {
        AssistantChatState.close()
        AssistantGlowState.visible = false
    }

    BackHandler(enabled = AssistantChatState.historyVisible) {
        AssistantChatState.closeHistory()
    }

    AnimatedVisibility(
        visible = AssistantChatState.visible,
        enter = slideInVertically(
            animationSpec = tween(320, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
        ) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(160)),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            val headerBg = MaterialTheme.colorScheme.background
            val headerBlurLayer = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .headerBackdropSource(headerBlurLayer)
                        .background(headerBg)
                ) {
                AssistantChatGlowBackground(Modifier.matchParentSize())
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        // 标题栏是渐变遮罩，列表从它下面穿过。
                        top = statusBarTop + 68.dp,
                        // 输入栏浮在列表上方；底部留白由尾部占位条目负责。
                        bottom = 0.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayItems, key = { it.key }) { display ->
                        when (display) {
                            is ChatDisplay.User -> UserBubble(display.item.text)
                            is ChatDisplay.Turn -> {
                                val streaming =
                                    (display.items.lastOrNull() as? AssistantChatItem.Assistant)
                                        ?.streaming == true
                                // 和操作栏使用同一个完成条件：整轮流式输出结束后才出现。
                                val roundDone = display.userItem?.id != activeUserId ||
                                    (!running && !streaming)
                                val showActions = display.key in roundEnds && roundDone
                                val notices = display.userItem?.id?.let(roundNotices::get)
                                val notice = when {
                                    !showActions -> null
                                    notices?.academicAnswer == true -> AI_ANSWER_NOTICE
                                    notices?.usedWorkDetail == true -> ACADEMIC_INTEGRITY_NOTICE
                                    else -> null
                                }
                                AssistantTurnBubble(
                                    items = display.items,
                                    expandedGroups = expandedGroups,
                                    onToggleGroup = { id ->
                                        expandedGroups = if (id in expandedGroups) {
                                            expandedGroups - id
                                        } else {
                                            expandedGroups + id
                                        }
                                    },
                                    expandedRows = expandedRows,
                                    onToggleRow = { id ->
                                        expandedRows = if (id in expandedRows) {
                                            expandedRows - id
                                        } else {
                                            expandedRows + id
                                        }
                                    },
                                    onOpenRoute = onOpenRoute,
                                    showRouteLinks = roundDone,
                                    showActions = showActions,
                                    notice = notice,
                                    actionsText = actionsTextOf(display.items),
                                    regenerateEnabled = !running,
                                    onRegenerate = {
                                        display.userItem?.let { AssistantChatState.regenerate(it.id) }
                                    },
                                )
                            }
                            is ChatDisplay.Approval -> ApprovalCard(
                                item = display.item,
                                onApprove = AssistantChatState::approve,
                                onReject = AssistantChatState::reject,
                            )
                            is ChatDisplay.Error -> ErrorCard(display.item.text)
                        }
                    }
                    if (status != null) {
                        item(key = "status") {
                            StatusShimmer(text = status)
                        }
                    }
                    // 稳定 key 的尾部占位：键盘弹出时只改它的高度，列表容器不重排。
                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(bottomBarHeight + 32.dp))
                    }
                }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .headerScrim(headerBg, headerBlurLayer)
                        .statusBarsPadding()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_assistant_sparkle_fill),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(ASSISTANT_NAME, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        AssistantHeaderIconButton(
                            icon = R.drawable.ic_assistant_history,
                            description = "历史对话",
                            onClick = AssistantChatState::openHistory,
                        )
                        Spacer(Modifier.width(8.dp))
                        AssistantHeaderIconButton(
                            icon = R.drawable.ic_assistant_close,
                            description = "关闭",
                            onClick = {
                                AssistantChatState.close()
                                AssistantGlowState.visible = false
                            },
                        )
                    }
                }

                // DS 式底栏：输入框 + 自身 IME/系统栏占位；列表用同高尾部占位。
                // 若系统在部分机型上仍然 resize 了窗口，有效 IME 高度会归零，避免双重上移。
                val effectiveIme = rememberEffectiveImePadding()
                val navBottomPx = WindowInsets.systemBarsIgnoringVisibility.getBottom(density)
                val barBottomInset = with(density) {
                    maxOf(effectiveIme.roundToPx(), navBottomPx).toDp()
                } + 10.dp
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeightPx = it.height }
                ) {
                    AssistantInputBar(
                        value = input,
                        onValueChange = { input = it },
                        onSend = {
                            val question = input
                            input = ""
                            if (question.isNotBlank()) AssistantChatState.send(question)
                        },
                        enabled = !running,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(barBottomInset)
                    )
                }

                AnimatedVisibility(
                    visible = AssistantChatState.historyVisible,
                    enter = slideInHorizontally(
                        animationSpec = tween(280, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
                    ) { it } + fadeIn(tween(160)),
                    exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(140)),
                ) {
                    AssistantHistoryPage(
                        sessions = AssistantChatState.sessions,
                        currentSessionId = AssistantChatState.currentSessionId,
                        onBack = AssistantChatState::closeHistory,
                        onNewChat = AssistantChatState::startNewSession,
                        onOpen = AssistantChatState::switchTo,
                        onDelete = AssistantChatState::deleteSession,
                    )
                }
            }
        }
    }
}

private sealed interface ChatDisplay {
    val key: String

    data class User(val item: AssistantChatItem.User) : ChatDisplay {
        override val key: String get() = item.id
    }

    data class Turn(
        val items: List<AssistantChatItem>,
        val userItem: AssistantChatItem.User?,
    ) : ChatDisplay {
        override val key: String get() = items.first().id
    }

    data class Approval(val item: AssistantChatItem.Approval) : ChatDisplay {
        override val key: String get() = item.id
    }

    data class Error(val item: AssistantChatItem.Error) : ChatDisplay {
        override val key: String get() = item.id
    }
}

private fun buildChatDisplay(items: List<AssistantChatItem>): List<ChatDisplay> {
    val result = mutableListOf<ChatDisplay>()
    val turn = mutableListOf<AssistantChatItem>()
    var lastUser: AssistantChatItem.User? = null

    fun flushTurn() {
        if (turn.isNotEmpty()) {
            result += ChatDisplay.Turn(turn.toList(), lastUser)
            turn.clear()
        }
    }

    items.forEach { item ->
        when (item) {
            is AssistantChatItem.Assistant,
            is AssistantChatItem.Reasoning,
            is AssistantChatItem.Tool,
            is AssistantChatItem.Usage -> turn += item
            is AssistantChatItem.User -> {
                flushTurn()
                lastUser = item
                result += ChatDisplay.User(item)
            }
            is AssistantChatItem.Approval -> {
                flushTurn()
                result += ChatDisplay.Approval(item)
            }
            is AssistantChatItem.Error -> {
                flushTurn()
                result += ChatDisplay.Error(item)
            }
        }
    }
    flushTurn()
    return result
}

/** 每一轮只把操作栏挂在最后一段回复下面，避免审批卡把一轮拆成两个操作栏。 */
private fun roundEndTurnKeys(displays: List<ChatDisplay>): Set<String> {
    val lastTurnByUser = mutableMapOf<String, String>()
    var currentUser: String? = null
    displays.forEach { display ->
        when (display) {
            is ChatDisplay.User -> currentUser = display.item.id
            is ChatDisplay.Turn -> currentUser?.let { lastTurnByUser[it] = display.key }
            else -> Unit
        }
    }
    return lastTurnByUser.values.toSet()
}

private fun actionsTextOf(items: List<AssistantChatItem>): String {
    val text = items.filterIsInstance<AssistantChatItem.Assistant>()
        .map { it.text.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
    if (text.isNotBlank()) return text
    return items.filterIsInstance<AssistantChatItem.Tool>()
        .mapNotNull { it.event.summary?.takeIf(String::isNotBlank) }
        .joinToString("\n")
}

@Composable
private fun UserBubble(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(colors.primary.copy(alpha = 0.82f), colors.primary)
                    )
                )
                .padding(8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
                color = colors.onPrimary,
            )
        }
    }
}

@Composable
private fun AssistantTurnBubble(
    items: List<AssistantChatItem>,
    expandedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    expandedRows: Set<String>,
    onToggleRow: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
    showRouteLinks: Boolean,
    showActions: Boolean,
    notice: String?,
    actionsText: String,
    regenerateEnabled: Boolean,
    onRegenerate: () -> Unit,
) {
    val segments = toolSegments(items)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        segments.forEach { segment ->
            segment.reasonings.forEach { reasoning ->
                ReasoningBlock(reasoning)
            }
            segment.assistant?.text?.takeIf { it.isNotBlank() }?.let { text ->
                AssistantMarkdown(
                    text = text,
                    streaming = segment.assistant.streaming,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (segment.tools.isNotEmpty()) {
                ToolCallGroup(
                    tools = segment.tools,
                    expanded = segment.key in expandedGroups,
                    onToggle = { onToggleGroup(segment.key) },
                    expandedRows = expandedRows,
                    onToggleRow = onToggleRow,
                )
            }
        }
        notice?.let { IntegrityNotice(it) }
        val routes = items
            .filterIsInstance<AssistantChatItem.Tool>()
            .mapNotNull { it.event.route }
            .distinct()
            .take(MAX_ROUTE_LINKS)
        if (showRouteLinks && routes.isNotEmpty()) {
            ToolRouteLinks(routes = routes, onOpenRoute = onOpenRoute)
        }
        if (showActions) {
            AssistantActionRow(
                text = actionsText,
                usageTokens = items
                    .filterIsInstance<AssistantChatItem.Usage>()
                    .lastOrNull()
                    ?.totalTokens,
                regenerateEnabled = regenerateEnabled,
                onRegenerate = onRegenerate,
            )
        }
    }
}

/** 学术诚信提示条；只在对应轮次出现，不做全局常驻。 */
@Composable
private fun IntegrityNotice(text: String) {
    val colors = MaterialTheme.colorScheme
    val lightSurface = colors.surface.luminance() > 0.5f
    val accent = if (lightSurface) Color(0xFF9A5A00) else Color(0xFFF0B45A)
    val container = if (lightSurface) Color(0x1AC2410C) else Color(0x26F59E0B)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = accent,
        )
    }
}

@Composable
private fun ReasoningBlock(item: AssistantChatItem.Reasoning) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember(item.id) { mutableStateOf(item.streaming) }
    var userToggled by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.streaming) {
        if (!item.streaming && !userToggled) expanded = false
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "reasoning-chevron",
    )
    val reasoningScroll = rememberScrollState()
    val consumeScrollChaining = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = available

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                available
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    userToggled = true
                    expanded = !expanded
                }
                .padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_assistant_brain),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (item.streaming) "思考中…" else "深度思考",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起思考过程" else "展开思考过程",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation),
            )
        }
        AnimatedVisibility(visible = expanded && item.text.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                color = colors.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Text(
                    text = item.text,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .nestedScroll(consumeScrollChaining)
                        .verticalScroll(reasoningScroll)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolCallGroup(
    tools: List<AssistantChatItem.Tool>,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandedRows: Set<String>,
    onToggleRow: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val completed = tools.count { it.event.ok }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "tool-group-chevron",
    )
    val groupScroll = rememberScrollState()
    // 组内列表到顶/到底时不要再把余量传给外层对话列表，避免页面跟着滚。
    val consumeScrollChaining = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = available

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                available
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_assistant_bot),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "调用工具 $completed/${tools.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起工具调用" else "展开工具调用",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                color = colors.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .nestedScroll(consumeScrollChaining)
                        .verticalScroll(groupScroll)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    tools.forEach { tool ->
                        ToolCallRow(
                            item = tool,
                            expanded = tool.id in expandedRows,
                            onToggle = { onToggleRow(tool.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCallRow(
    item: AssistantChatItem.Tool,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val event = item.event
    val expandable = event.card != null || !event.summary.isNullOrBlank()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "tool-row-chevron",
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (expandable) Modifier.clickable(onClick = onToggle) else Modifier
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (event.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                contentDescription = null,
                tint = if (event.ok) Color(0xFF2E9E6B) else colors.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = event.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expandable) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起结果" else "查看结果",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(chevronRotation),
                )
            }
        }
        AnimatedVisibility(visible = expanded && expandable) {
            Column(
                modifier = Modifier.padding(start = 26.dp, top = 4.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!event.ok) {
                    Text(
                        event.summary ?: "执行失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                } else if (event.card != null) {
                    AssistantCardView(event.card)
                } else {
                    Text(
                        event.summary.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface,
                    )
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                Text(
                    buildString {
                        append("来源：")
                        append(event.source)
                        append(" · 更新于 ")
                        append(formatTimestamp(event.fetchedAt))
                        if (event.trust == AssistantTrust.UNTRUSTED) {
                            append(" · 外部内容，仅作参考")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (event.trust == AssistantTrust.UNTRUSTED) {
                        colors.error
                    } else {
                        colors.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolRouteLinks(routes: List<String>, onOpenRoute: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        routes.forEach { route ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenRoute(route) }
                    .drawBehind {
                        val y = size.height - 1.dp.toPx()
                        drawLine(
                            color = colors.primary.copy(alpha = 0.55f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(1.5.dp.toPx(), 2.dp.toPx()),
                                0f,
                            ),
                        )
                    }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    routeLabel(route),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Rounded.NorthEast,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

private fun routeLabel(route: String): String = when (route) {
    "schedule" -> "打开课表"
    "grade" -> "打开成绩"
    "exam" -> "打开考试"
    "free_classroom" -> "打开空闲教室"
    "attendance_records" -> "打开考勤"
    "program_completion" -> "打开培养方案"
    "evaluation" -> "打开评教"
    "campus_notices" -> "打开通知"
    "lost_found" -> "打开失物招领"
    "canteen_ranking" -> "打开必吃榜"
    "phone_book" -> "打开电话本"
    "student_mail" -> "打开邮箱"
    "billing" -> "打开账单"
    "weather" -> "打开天气"
    "school_calendar" -> "打开校历"
    else -> "打开页面"
}

@Composable
private fun AssistantActionRow(
    text: String,
    usageTokens: Int?,
    regenerateEnabled: Boolean,
    onRegenerate: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantMiniAction(R.drawable.ic_assistant_copy, "复制") {
            clipboard.setText(AnnotatedString(text))
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        AssistantMiniAction(R.drawable.ic_assistant_share, "转发") {
            if (text.isNotBlank()) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                runCatching {
                    context.startActivity(
                        Intent.createChooser(send, "转发回答")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        AssistantMiniAction(
            icon = R.drawable.ic_assistant_refresh,
            description = "重新回答",
            enabled = regenerateEnabled,
            onClick = onRegenerate,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .width(1.dp)
                .height(12.dp)
                .background(colors.outlineVariant)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "AI生成答案，可能有误",
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Light,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        usageTokens?.takeIf { it > 0 }?.let { tokens ->
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(colors.outlineVariant)
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(R.drawable.ic_assistant_sparkle_mini),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(10.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                formatTokenCount(tokens),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Light,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AssistantMiniAction(
    @DrawableRes icon: Int,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(14.dp),
        )
    }
}


@Composable
private fun AssistantCardView(card: AssistantCard) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (card.headline != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                card.headlinePrefix?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.primary,
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    card.headline,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primary,
                )
                card.headlineSuffix?.let {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.primary,
                    )
                }
            }
        }
        card.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (card.stats.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                card.stats.forEach { stat ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stat.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            stat.value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                    }
                }
            }
        }
        card.items.forEachIndexed { index, item ->
            val previousGroup = card.items.getOrNull(index - 1)?.group
            if (item.group != null && item.group != previousGroup) {
                Text(
                    item.group,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
            }
            CardItemRow(item)
        }
        card.note?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardItemRow(item: AssistantCardItem) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
            )
            item.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        item.value?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.width(10.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }
        item.status?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.width(10.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = when (item.tone) {
                    AssistantCardTone.SUCCESS -> Color(0xFF2E9E6B)
                    AssistantCardTone.DANGER -> colors.error
                    AssistantCardTone.ACCENT -> colors.primary
                    AssistantCardTone.NEUTRAL -> colors.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun StatusShimmer(text: String) {
    val colors = MaterialTheme.colorScheme
    val base = colors.onSurfaceVariant
    val phase by rememberInfiniteTransition(label = "status-shimmer")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_400, easing = LinearEasing)
            ),
            label = "status-shimmer-phase"
        )
    var textWidth by remember { mutableIntStateOf(0) }
    val brush = Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.35f),
            base.copy(alpha = 0.95f),
            base.copy(alpha = 0.35f),
        ),
        start = Offset(-textWidth + phase * textWidth * 2f, 0f),
        end = Offset(phase * textWidth * 2f, 0f),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.onSizeChanged { textWidth = it.width },
            style = MaterialTheme.typography.bodyMedium.copy(brush = brush),
            maxLines = 2,
        )
    }
}

@Composable
private fun ApprovalCard(
    item: AssistantChatItem.Approval,
    onApprove: (String?) -> Unit,
    onReject: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val request = item.request
    val pending = item.decision == AssistantApprovalDecision.PENDING
    val needsPassword = request.requiresPaymentPassword
    var password by remember(item.id) { mutableStateOf("") }
    val statusText = when (item.decision) {
        AssistantApprovalDecision.PENDING -> "等待确认"
        AssistantApprovalDecision.APPROVED -> "已确认"
        AssistantApprovalDecision.REJECTED -> "已取消"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_assistant_sparkle_fill),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "需要你确认",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pending) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }

            Text(
                request.summary.take(400),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
            )
            request.warning?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.error)
            }
            request.arguments.entrySet().forEach { (key, value) ->
                if (key != "paymentPassword" && key != "password") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            labelFor(key),
                            modifier = Modifier.width(64.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            approvalValue(key, value.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                        )
                    }
                }
            }
            if (pending && needsPassword) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { input -> password = input.filter(Char::isDigit).take(6) },
                    label = { Text("校园卡支付密码（6 位）") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    supportingText = {
                        Text(
                            "只在本机确认时使用，不保存、不发给模型。",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                request.risk,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainer)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            if (pending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onReject) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val secret = password.takeIf { needsPassword && it.length == 6 }
                            password = ""
                            onApprove(secret)
                        },
                        enabled = !needsPassword || password.length == 6,
                    ) {
                        Text(request.confirmationAction)
                    }
                }
            }
        }
    }
}

private fun labelFor(key: String): String = when (key) {
    "amount" -> "金额"
    "bank" -> "银行卡"
    "bathroom" -> "浴室"
    "phone" -> "手机号"
    "to" -> "收件人"
    "cc" -> "抄送"
    "subject" -> "主题"
    "body" -> "正文"
    "teacher" -> "教师"
    "all" -> "全部"
    "semester" -> "学期"
    "title" -> "标题"
    "description" -> "描述"
    "type" -> "类型"
    "campus" -> "校区"
    "state" -> "类型"
    "id" -> "ID"
    "index" -> "序号"
    "question" -> "题目"
    "label" -> "名称"
    "room" -> "房间"
    else -> key
}

private fun approvalValue(key: String, raw: String): String {
    val value = raw.trim('"')
    return when (key) {
        "amount" -> "¥$value"
        "bank" -> when (value) {
            "agricultural", "agricultural_bank" -> "中国农业银行"
            "cmb", "china_merchants_bank" -> "招商银行"
            "alipay" -> "支付宝"
            else -> value
        }
        "state" -> when (value) {
            "1" -> "失物招领"
            "2" -> "寻物启事"
            else -> value
        }
        else -> value.take(160)
    }
}

@Composable
private fun ErrorCard(text: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.error,
        )
    }
}

private suspend fun runStream(
    service: AssistantService,
    scope: kotlinx.coroutines.CoroutineScope,
    question: String,
) {
    AssistantChatState.updateRunning(true)
    AssistantChatState.updateStatus(null)
    var streamingId: String? = null
    var reasoningId: String? = null
    val pending = StringBuilder()
    var flushJob: Job? = null

    fun finishReasoningNow() {
        reasoningId?.let { AssistantChatState.finishReasoning(it) }
        reasoningId = null
    }

    /** 本轮（最后一条用户消息之后）是否刚同意了「解题确认」。 */
    fun academicAnswerPending(): Boolean {
        val items = AssistantChatState.items
        val lastUserIndex = items.indexOfLast { it is AssistantChatItem.User }
        val approval = items.drop(lastUserIndex + 1)
            .lastOrNull { it is AssistantChatItem.Approval } as? AssistantChatItem.Approval
            ?: return false
        return approval.request.toolId == "confirm_homework_help" &&
            approval.decision == AssistantApprovalDecision.APPROVED
    }

    fun flushNow() {
        flushJob?.cancel()
        flushJob = null
        if (pending.isEmpty()) return
        val chunk = pending.toString()
        pending.setLength(0)
        val id = streamingId
            ?: AssistantChatState.startAssistant(academicAnswerPending()).also { streamingId = it }
        AssistantChatState.appendDelta(id, chunk)
    }

    fun enqueue(delta: String) {
        pending.append(delta)
        if (flushJob == null) {
            flushJob = scope.launch {
                while (true) {
                    delay(DELTA_FLUSH_INTERVAL_MS)
                    if (pending.isEmpty()) {
                        flushJob = null
                        return@launch
                    }
                    val chunk = pending.toString()
                    pending.setLength(0)
                    val id = streamingId
                        ?: AssistantChatState.startAssistant(academicAnswerPending()).also { streamingId = it }
                    AssistantChatState.appendDelta(id, chunk)
                }
            }
        }
    }

    val gate = com.ahu.ahutong.assistant.safety.AssistantApprovalGate()
    AssistantChatState.attachGate(gate)
    val history = AssistantChatState.consumeHistory()
    try {
        service.stream(question, history, gate).collect { event ->
            when (event) {
                is AssistantServiceEvent.Status -> AssistantChatState.updateStatus(event.text)
                AssistantServiceEvent.AssistantStart -> {
                    flushNow()
                    // 不在这里创建空回答项：思考链可能先到，先建空 assistant 会排到思考链前面。
                    AssistantChatState.updateStatus(null)
                }
                is AssistantServiceEvent.AssistantDelta -> enqueue(event.text)
                is AssistantServiceEvent.ReasoningDelta -> {
                    val id = reasoningId ?: AssistantChatState.startReasoning().also { reasoningId = it }
                    AssistantChatState.appendReasoning(id, event.text)
                }
                is AssistantServiceEvent.AssistantMessage -> {
                    finishReasoningNow()
                    flushNow()
                    val id = streamingId
                    if (id != null) {
                        AssistantChatState.finishAssistant(id, event.text)
                        streamingId = null
                    } else {
                        AssistantChatState.appendMessage(event.text, academicAnswerPending())
                    }
                    AssistantChatState.updateStatus(null)
                }
                is AssistantServiceEvent.ToolResult -> {
                    finishReasoningNow()
                    flushNow()
                    streamingId?.let {
                        AssistantChatState.finishAssistant(it, null)
                        streamingId = null
                    }
                    AssistantChatState.appendTool(event)
                    AssistantChatState.updateStatus(null)
                }
                is AssistantServiceEvent.ApprovalRequired -> {
                    finishReasoningNow()
                    flushNow()
                    streamingId?.let {
                        AssistantChatState.finishAssistant(it, null)
                        streamingId = null
                    }
                    AssistantChatState.appendApproval(event.request)
                    AssistantChatState.updateStatus(null)
                }
                is AssistantServiceEvent.Usage -> {
                    AssistantChatState.appendUsage(event.totalTokens)
                }
                is AssistantServiceEvent.Failed -> {
                    finishReasoningNow()
                    flushNow()
                    streamingId?.let {
                        AssistantChatState.finishAssistant(it, null)
                        streamingId = null
                    }
                    AssistantChatState.appendError(event.message)
                    AssistantChatState.updateStatus(null)
                }
                AssistantServiceEvent.Done -> {
                    finishReasoningNow()
                    flushNow()
                    streamingId?.let {
                        AssistantChatState.finishAssistant(it, null)
                        streamingId = null
                    }
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        flushNow()
        AssistantChatState.appendError(error.message ?: "助手执行失败")
    } finally {
        flushJob?.cancel()
        flushJob = null
        AssistantChatState.finishStreaming()
        AssistantChatState.updateRunning(false)
        AssistantChatState.updateStatus(null)
        AssistantChatState.persist()
    }
}

private fun formatTimestamp(epochMillis: Long): String = runCatching {
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(epochMillis))
}.getOrDefault("")

private fun formatTokenCount(tokens: Int): String {
    val divisor = when {
        tokens >= 1_000_000_000 -> 1_000_000_000L
        tokens >= 1_000_000 -> 1_000_000L
        tokens > 1_000 -> 1_000L
        else -> return "$tokens Toks"
    }
    val value = BigDecimal(tokens)
        .divide(BigDecimal(divisor), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    val suffix = when (divisor) {
        1_000L -> "k"
        1_000_000L -> "m"
        else -> "b"
    }
    return "$value$suffix Toks"
}

private const val ACADEMIC_INTEGRITY_NOTICE =
    "学术诚信提醒：请善用工具，建议您独立思考完成作业。"
private const val AI_ANSWER_NOTICE =
    "答案由AI生成，仅供参考；安大通倡导学术诚信，不鼓励任何形式的抄袭行为。"

private const val MAX_ROUTE_LINKS = 3
private const val ASSISTANT_NAME = "AHUTong Intelligence"
private const val DELTA_FLUSH_INTERVAL_MS = 33L
