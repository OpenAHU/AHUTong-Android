package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.assistant.AssistantCapabilityRegistry
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantService
import com.ahu.ahutong.assistant.AssistantTurn
import com.ahu.ahutong.assistant.capabilities.CardBalanceCapability
import com.ahu.ahutong.assistant.capabilities.FreeClassroomCapability
import com.ahu.ahutong.assistant.capabilities.TodayScheduleCapability
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.ui.glow.AssistantGlowState
import com.ahu.ahutong.ui.state.ScheduleViewModel
import kotlinx.coroutines.launch

/**
 * AI 界面 = 屏幕边缘光效 + 底部输入栏，没有弹层/遮罩。
 * 输入栏只负责发消息与展示最近一条结果，执行仍留在各功能原有页面。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantOverlay(
    scheduleViewModel: ScheduleViewModel,
    onOpenRoute: (String) -> Unit,
) {
    val registry = remember {
        AssistantCapabilityRegistry(
            listOf(
                TodayScheduleCapability(
                    config = { scheduleViewModel.scheduleConfig.value },
                    cached = { AHURepository.getCachedSchedule() },
                ),
                FreeClassroomCapability(),
                CardBalanceCapability(),
            )
        )
    }
    val service = remember(registry) { AssistantService(registry) }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var turn by remember { mutableStateOf<AssistantTurn?>(null) }

    fun ask(question: String) {
        if (running || question.isBlank()) return
        running = true
        scope.launch {
            try {
                turn = service.ask(question)
            } finally {
                running = false
            }
        }
    }

    AnimatedVisibility(
        visible = AssistantGlowState.visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 4 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 4 },
    ) {
        Box(Modifier.fillMaxSize()) {
            val imeVisible = WindowInsets.isImeVisible
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 10.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                turn?.let { current ->
                    AssistantResultBubble(
                        turn = current,
                        onOpenRoute = onOpenRoute,
                        onRetry = { ask(current.question) },
                    )
                }
                AssistantInputBar(
                    value = input,
                    onValueChange = { input = it },
                    onSend = {
                        val question = input
                        input = ""
                        ask(question)
                    },
                    enabled = !running,
                )
            }
        }
    }
}

@Composable
private fun AssistantResultBubble(
    turn: AssistantTurn,
    onOpenRoute: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val outcome = turn.outcome
    Surface(
        color = Color(0xE61A1A1C),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (outcome) {
                null -> Text(
                    text = "第一版只会回答课表、空教室和校园卡余额。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is AssistantOutcome.Success -> {
                    Text(
                        text = outcome.answer.summary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    outcome.answer.lines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                    outcome.answer.route?.let { route ->
                        TextButton(
                            onClick = { onOpenRoute(route) },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = Color.White
                            )
                        ) { Text("打开页面") }
                    }
                }
                is AssistantOutcome.Failure -> {
                    Text(
                        text = outcome.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFF8A80),
                    )
                    if (outcome.retryable) {
                        TextButton(
                            onClick = onRetry,
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = Color.White
                            )
                        ) { Text("再试一次") }
                    }
                }
            }
        }
    }
}
