package com.ahu.ahutong.ui.screen.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FactCheck
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.assistant.AssistantCard
import com.ahu.ahutong.assistant.AssistantCardItem
import com.ahu.ahutong.assistant.AssistantCardTone
import com.ahu.ahutong.assistant.AssistantService
import com.ahu.ahutong.assistant.AssistantServiceEvent
import com.ahu.ahutong.assistant.AssistantTrust
import com.ahu.ahutong.ui.glow.AssistantGlowState
import com.ahu.ahutong.ui.state.ScheduleViewModel
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
    var stickToBottom by remember { mutableStateOf(true) }
    var expandedTools by remember { mutableStateOf<Set<String>>(emptySet()) }

    val items = AssistantChatState.items
    val status = AssistantChatState.status
    val running = AssistantChatState.running
    val displayItems = buildChatDisplay(items)
    val lastStreaming = (items.lastOrNull() as? AssistantChatItem.Assistant)?.streaming == true
    val streamingLength = (items.lastOrNull() as? AssistantChatItem.Assistant)?.text?.length ?: 0

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canForward) ->
                if (scrolling) stickToBottom = !canForward
            }
    }

    LaunchedEffect(displayItems.size, status, lastStreaming) {
        if (!stickToBottom) return@LaunchedEffect
        val target = displayItems.lastIndex + if (status != null) 1 else 0
        if (target < 0) return@LaunchedEffect
        withFrameNanos { }
        listState.scrollToItem(target)
        var stableFrames = 0
        repeat(BOTTOM_SETTLE_MAX_FRAMES) {
            if (stableFrames >= BOTTOM_SETTLE_STABLE_FRAMES) return@LaunchedEffect
            withFrameNanos { }
            if (!listState.isScrollInProgress && listState.canScrollForward) {
                listState.dispatchRawDelta(BOTTOM_JUMP_PX)
                stableFrames = 0
            } else {
                stableFrames++
            }
        }
    }

    LaunchedEffect(streamingLength) {
        if (streamingLength == 0 || !stickToBottom) return@LaunchedEffect
        withFrameNanos { }
        listState.dispatchRawDelta(BOTTOM_JUMP_PX)
    }

    LaunchedEffect(AssistantChatState.pendingQuestion) {
        val question = AssistantChatState.consumePending() ?: return@LaunchedEffect
        AssistantChatState.attachJob(scope.launch { runStream(service, scope, question) })
    }

    BackHandler(enabled = AssistantChatState.visible) {
        AssistantChatState.close()
        AssistantGlowState.visible = false
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
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
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
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(ASSISTANT_NAME, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        AssistantChatState.close()
                        AssistantGlowState.visible = false
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 6.dp,
                        bottom = 130.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayItems, key = { it.key }) { display ->
                        when (display) {
                            is ChatDisplay.User -> UserBubble(display.item.text)
                            is ChatDisplay.Turn -> AssistantTurnBubble(
                                items = display.items,
                                expandedTools = expandedTools,
                                onToggleTool = { id ->
                                    expandedTools = if (id in expandedTools) {
                                        expandedTools - id
                                    } else {
                                        expandedTools + id
                                    }
                                },
                                onOpenRoute = onOpenRoute,
                            )
                            is ChatDisplay.Error -> ErrorCard(display.item.text)
                        }
                    }
                    if (status != null) {
                        item(key = "status") {
                            StatusPill(text = status, onCancel = AssistantChatState::cancelRun)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp + rememberEffectiveImePadding())
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

    data class Turn(val items: List<AssistantChatItem>) : ChatDisplay {
        override val key: String get() = items.first().id
    }

    data class Error(val item: AssistantChatItem.Error) : ChatDisplay {
        override val key: String get() = item.id
    }
}

private fun buildChatDisplay(items: List<AssistantChatItem>): List<ChatDisplay> {
    val result = mutableListOf<ChatDisplay>()
    val turn = mutableListOf<AssistantChatItem>()

    fun flushTurn() {
        if (turn.isNotEmpty()) {
            result += ChatDisplay.Turn(turn.toList())
            turn.clear()
        }
    }

    items.forEach { item ->
        when (item) {
            is AssistantChatItem.Assistant, is AssistantChatItem.Tool -> turn += item
            is AssistantChatItem.User -> {
                flushTurn()
                result += ChatDisplay.User(item)
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

@Composable
private fun UserBubble(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(colors.primary.copy(alpha = 0.82f), colors.primary)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
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
    expandedTools: Set<String>,
    onToggleTool: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        var hasContent = false
        items.forEach { item ->
            when (item) {
                is AssistantChatItem.Assistant -> {
                    if (item.text.isNotBlank()) {
                        if (hasContent) {
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                        }
                        AssistantMarkdown(
                            text = item.text,
                            streaming = item.streaming,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        hasContent = true
                    }
                }
                is AssistantChatItem.Tool -> {
                    if (hasContent) {
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                    }
                    ToolCard(
                        item = item,
                        expanded = item.id in expandedTools,
                        onToggle = { onToggleTool(item.id) },
                        onOpenRoute = onOpenRoute,
                    )
                    hasContent = true
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ToolCard(
    item: AssistantChatItem.Tool,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val event = item.event
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (event.ok) colors.primaryContainer else colors.errorContainer
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = toolIcon(event.toolId),
                        contentDescription = null,
                        tint = if (event.ok) colors.primary else colors.error,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                    )
                    Text(
                        when {
                            !event.ok -> "执行失败"
                            event.navigateOnly -> "需要确认"
                            else -> "执行成功"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (event.ok) colors.primary else colors.error,
                    )
                }
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (expanded) "收起" else "查看",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                event.route?.let { route ->
                    IconButton(
                        onClick = { onOpenRoute(route) },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NorthEast,
                            contentDescription = "打开页面",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 2.dp),
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
private fun StatusPill(text: String, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = colors.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.6.dp,
                    color = colors.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "取消",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
        }
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
    val pending = StringBuilder()
    var flushJob: Job? = null

    fun flushNow() {
        flushJob?.cancel()
        flushJob = null
        if (pending.isEmpty()) return
        val chunk = pending.toString()
        pending.setLength(0)
        val id = streamingId ?: AssistantChatState.startAssistant().also { streamingId = it }
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
                    val id = streamingId ?: AssistantChatState.startAssistant().also { streamingId = it }
                    AssistantChatState.appendDelta(id, chunk)
                }
            }
        }
    }

    try {
        service.stream(question).collect { event ->
            when (event) {
                is AssistantServiceEvent.Status -> AssistantChatState.updateStatus(event.text)
                AssistantServiceEvent.AssistantStart -> {
                    flushNow()
                    if (streamingId == null) {
                        streamingId = AssistantChatState.startAssistant()
                    }
                    AssistantChatState.updateStatus(null)
                }
                is AssistantServiceEvent.AssistantDelta -> enqueue(event.text)
                is AssistantServiceEvent.AssistantMessage -> {
                    flushNow()
                    val id = streamingId
                    if (id != null) {
                        AssistantChatState.finishAssistant(id, event.text)
                        streamingId = null
                    } else {
                        AssistantChatState.appendMessage(event.text)
                    }
                    AssistantChatState.updateStatus(null)
                }
                is AssistantServiceEvent.ToolResult -> {
                    flushNow()
                    streamingId?.let {
                        AssistantChatState.finishAssistant(it, null)
                        streamingId = null
                    }
                    AssistantChatState.appendTool(event)
                    AssistantChatState.updateStatus(null)
                }
                is AssistantServiceEvent.Failed -> {
                    flushNow()
                    streamingId?.let {
                        AssistantChatState.finishAssistant(it, null)
                        streamingId = null
                    }
                    AssistantChatState.appendError(event.message)
                    AssistantChatState.updateStatus(null)
                }
                AssistantServiceEvent.Done -> {
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
    }
}

private fun toolIcon(name: String): ImageVector = when (name) {
    "get_schedule" -> Icons.Rounded.CalendarMonth
    "get_assignments" -> Icons.Rounded.Assignment
    "get_grades", "get_gpa_rank" -> Icons.Rounded.School
    "get_balance" -> Icons.Rounded.AccountBalanceWallet
    "get_bills" -> Icons.Rounded.ReceiptLong
    "get_exams" -> Icons.Rounded.Quiz
    "get_electricity", "get_electricity_conditions", "switch_electricity_condition" -> Icons.Rounded.Bolt
    "get_weather" -> Icons.Rounded.WbSunny
    "get_attendance" -> Icons.Rounded.FactCheck
    "get_program_completion" -> Icons.Rounded.TrackChanges
    "get_lost_found", "publish_lost_found", "delete_lost_found" -> Icons.Rounded.Search
    "get_notices" -> Icons.Rounded.Campaign
    "get_free_rooms" -> Icons.Rounded.MeetingRoom
    "get_phone_book" -> Icons.Rounded.Phone
    "get_mail", "send_mail" -> Icons.Rounded.Email
    "get_canteen_ranking" -> Icons.Rounded.Restaurant
    "get_course_tasks" -> Icons.Rounded.MenuBook
    "get_evaluation_tasks", "submit_evaluation" -> Icons.Rounded.RateReview
    "recharge_card", "recharge_electricity", "recharge_bathroom", "recharge_network" -> Icons.Rounded.Payments
    "add_calendar_event" -> Icons.Rounded.Event
    else -> Icons.Rounded.AutoAwesome
}

private fun formatTimestamp(epochMillis: Long): String = runCatching {
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(epochMillis))
}.getOrDefault("")

private const val ASSISTANT_NAME = "AhuTong Intelligence"
private const val DELTA_FLUSH_INTERVAL_MS = 33L
private const val BOTTOM_JUMP_PX = 100_000f
private const val BOTTOM_SETTLE_MAX_FRAMES = 60
private const val BOTTOM_SETTLE_STABLE_FRAMES = 5
