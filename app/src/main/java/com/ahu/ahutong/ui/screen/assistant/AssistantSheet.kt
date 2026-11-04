package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.ahu.ahutong.ui.state.ScheduleViewModel
import kotlinx.coroutines.launch

private val SUGGESTIONS = listOf(
    "今天有什么课",
    "哪有空教室",
    "校园卡余额",
)

/**
 * AI 入口的第一版：确定性问句 → 能力层 → 结构化结果。
 * 结果里的"打开页面"只做导航，执行仍留在各功能原有页面。
 */
@Composable
fun AssistantSheet(
    scheduleViewModel: ScheduleViewModel,
    onDismiss: () -> Unit,
    onOpenRoute: (String) -> Unit
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

    AppModalBottomSheet(title = "AI 助手", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "直接问，结果来自现有功能的数据；不会替你执行充值等操作。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.forEach { suggestion ->
                    TextButton(onClick = {
                        input = suggestion
                        ask(suggestion)
                    }) {
                        Text(suggestion, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            turn?.let { current ->
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AssistantAnswerBlock(
                        turn = current,
                        onOpenRoute = onOpenRoute,
                        onRetry = { ask(current.question) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            AssistantInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    val question = input
                    input = ""
                    ask(question)
                },
                enabled = !running
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AssistantAnswerBlock(
    turn: AssistantTurn,
    onOpenRoute: (String) -> Unit,
    onRetry: () -> Unit
) {
    val outcome = turn.outcome
    if (outcome == null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "第一版只会回答课表、空教室和校园卡余额。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp)
            )
        }
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (outcome) {
                is AssistantOutcome.Success -> {
                    Text(
                        text = outcome.answer.summary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    outcome.answer.lines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    outcome.answer.route?.let { route ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onOpenRoute(route) }) {
                                Text("打开页面")
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
                is AssistantOutcome.Failure -> {
                    Text(
                        text = outcome.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (outcome.retryable) {
                        TextButton(onClick = onRetry) { Text("再试一次") }
                    }
                }
            }
        }
    }
}
