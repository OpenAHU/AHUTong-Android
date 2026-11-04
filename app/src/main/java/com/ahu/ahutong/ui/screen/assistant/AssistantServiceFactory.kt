package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ahu.ahutong.assistant.AssistantCapabilityRegistry
import com.ahu.ahutong.assistant.AssistantService
import com.ahu.ahutong.assistant.capabilities.CardBalanceCapability
import com.ahu.ahutong.assistant.capabilities.FreeClassroomCapability
import com.ahu.ahutong.assistant.capabilities.TodayScheduleCapability
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.ui.state.ScheduleViewModel

/** 底部入口与全屏问答页共用同一份能力注册表。 */
@Composable
internal fun rememberAssistantService(scheduleViewModel: ScheduleViewModel): AssistantService {
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
    return remember(registry) { AssistantService(registry) }
}
