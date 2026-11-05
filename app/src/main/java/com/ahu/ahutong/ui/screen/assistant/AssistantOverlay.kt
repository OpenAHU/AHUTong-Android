package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.glow.AssistantGlowState
import com.ahu.ahutong.ui.state.ScheduleViewModel

/**
 * AI 界面入口：光效层底部的输入栏。发送后进入全屏问答页，后续对话都在那里发生。
 *
 * 输入法避让用 `ime ∪ navigationBars` 取并集，避免两段 inset 叠加导致过高。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantOverlay(
    scheduleViewModel: ScheduleViewModel,
    onOpenRoute: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    // 等彩色光带扩散到底部，再把输入栏送上来。
    val visible = AssistantGlowState.visible &&
        AssistantGlowState.revealed &&
        !AssistantChatState.visible

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 4 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 4 },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .swipeUpToOpen { AssistantChatState.openBlank() }
        ) {
            // 脆弱层：点输入栏以外的任何地方就退出 AI 界面（不加遮罩）。
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { AssistantGlowState.visible = false }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp + rememberEffectiveImePadding())
                    // 吸收点击，避免穿透到下面的脆弱层。
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
            ) {
                // 上滑提示条：从 AI 层上滑即可进入全屏对话页。
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp)
                        .width(72.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f))
                )
                AssistantInputBar(
                    value = input,
                    onValueChange = { input = it },
                    onSend = {
                        val question = input
                        input = ""
                        if (question.isNotBlank()) {
                            AssistantChatState.open(question)
                        }
                    },
                )
            }
        }
    }
}

/**
 * 在 Initial pass 里观察纵向滑动，避免输入框或可点击层先消费事件；
 * 越过阈值就进入对话页，短滑/点击不受影响。
 */
private fun Modifier.swipeUpToOpen(
    threshold: Dp = 56.dp,
    onSwipeUp: () -> Unit,
): Modifier = pointerInput(threshold, onSwipeUp) {
    val thresholdPx = threshold.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var total = 0f
        var fired = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.positionChanged()) {
                total += change.positionChange().y
                if (!fired && total <= -thresholdPx) {
                    fired = true
                    onSwipeUp()
                }
            }
            if (!change.pressed) break
        }
    }
}
