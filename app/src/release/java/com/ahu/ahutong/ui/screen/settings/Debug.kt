package com.ahu.ahutong.ui.screen.settings

import androidx.compose.runtime.Composable
import com.ahu.ahutong.ui.state.DiscoveryViewModel
import com.ahu.ahutong.ui.state.ScheduleViewModel

/** Release builds intentionally contain no debug controls. */
@Composable
fun Debug(
    scheduleViewModel: ScheduleViewModel,
    discoveryViewModel: DiscoveryViewModel,
    onGrayStateChanged: () -> Unit = {},
    onBack: (() -> Unit)? = null
) = Unit
