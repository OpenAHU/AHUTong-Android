package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ahu.ahutong.assistant.AssistantCapabilityRegistry
import com.ahu.ahutong.assistant.AssistantService
import com.ahu.ahutong.assistant.capabilities.AssistantCapabilities
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.ui.state.ScheduleViewModel

/** 底部入口与全屏问答页共用同一份能力注册表。 */
@Composable
internal fun rememberAssistantService(scheduleViewModel: ScheduleViewModel): AssistantService {
    val registry = remember {
        AssistantCapabilityRegistry(
            AssistantCapabilities.all(
                config = { scheduleViewModel.scheduleConfig.value },
                cachedSchedule = { AHURepository.getCachedSchedule() },
            )
        )
    }
    return remember(registry) { AssistantService(registry) }
}
