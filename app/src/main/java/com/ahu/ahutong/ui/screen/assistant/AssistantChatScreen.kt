package com.ahu.ahutong.ui.screen.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantTurn
import com.ahu.ahutong.ui.glow.AssistantGlowState
import com.ahu.ahutong.ui.state.ScheduleViewModel
import kotlinx.coroutines.launch

/**
 * 全屏 AI 问答页：用户发送第一条消息后从底部向上展开，后续对话都在这里发生。
 * 复用能力层，沿用同一套只读工具；需要执行的动作仍跳回原页面。
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
    var running by remember { mutableStateOf(false) }

    fun respond(question: String) {
        if (running) return
        running = true
        scope.launch {
            try {
                AssistantChatState.append(service.ask(question).toChatMessage())
            } finally {
                running = false
            }
        }
    }

    LaunchedEffect(AssistantChatState.pendingQuestion) {
        val question = AssistantChatState.consumePending() ?: return@LaunchedEffect
        respond(question)
    }
    LaunchedEffect(AssistantChatState.messages.size) {
        val lastIndex = AssistantChatState.messages.lastIndex
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
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
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("AI 助手", style = MaterialTheme.typography.titleMedium)
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(AssistantChatState.messages) { message ->
                        ChatBubble(message = message, onOpenRoute = onOpenRoute)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                        .padding(bottom = 10.dp + rememberEffectiveImePadding())
                ) {
                    AssistantInputBar(
                        value = input,
                        onValueChange = { input = it },
                        onSend = {
                            val question = input
                            input = ""
                            if (question.isNotBlank()) {
                                AssistantChatState.append(AssistantChatMessage.User(question))
                                respond(question)
                            }
                        },
                        enabled = !running,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: AssistantChatMessage,
    onOpenRoute: (String) -> Unit,
) {
    when (message) {
        is AssistantChatMessage.User -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        is AssistantChatMessage.Assistant -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = message.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    message.lines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    message.route?.let { route ->
                        TextButton(onClick = { onOpenRoute(route) }) { Text("打开页面") }
                    }
                }
            }
        }
    }
}

private fun AssistantTurn.toChatMessage(): AssistantChatMessage = when (val result = outcome) {
    null -> AssistantChatMessage.Assistant(
        "我可以查课表、空教室、成绩、绩点排名、考试、余额、账单、电费、考勤、培养方案、评教、通知、失物招领、邮箱、电话本、作业、章节进度、天气和必吃榜；" +
            "充值、评教提交、发邮件等会打开原页面由你确认。"
    )
    is AssistantOutcome.Success -> AssistantChatMessage.Assistant(
        summary = result.answer.summary,
        lines = result.answer.lines,
        route = result.answer.route,
    )
    is AssistantOutcome.Failure -> AssistantChatMessage.Assistant(summary = result.message)
}
