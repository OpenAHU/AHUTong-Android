package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.components.SettingsToggleRow
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.kyant.backdrop.Backdrop

/**
 * 智能体验：快捷建议与预加载开关。挂在 AI 与智能体验设置页，也兼容旧入口。
 */
@Composable
fun IntelligenceSettingsSection(
    backdrop: Backdrop,
    onHorizontalDragActiveChange: (Boolean) -> Unit = {},
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val personalizationEnabled by viewModel.personalizationEnabled.collectAsState()
    val predictivePrefetchEnabled by viewModel.predictivePrefetchEnabled.collectAsState()
    val wifiOnlyPrefetch by viewModel.wifiOnlyPrefetch.collectAsState()

    SettingsSection(
        title = "智能体验",
        modifier = Modifier.padding(horizontal = 16.dp),
        backdrop = backdrop
    ) {
        personalizationEnabled?.let { enabled ->
            SettingsToggleRow(
                title = "显示快捷建议",
                selected = enabled,
                onSelectedChange = viewModel::setPersonalizationEnabled,
                backdrop = backdrop,
                onHorizontalDragActiveChange = onHorizontalDragActiveChange
            )
        }
        val predictiveEnabled = predictivePrefetchEnabled
        val wifiOnly = wifiOnlyPrefetch
        if (predictiveEnabled != null && wifiOnly != null) {
            SettingsToggleRow(
                title = "提前加载预测内容",
                selected = predictiveEnabled,
                onSelectedChange = viewModel::setPredictivePrefetchEnabled,
                backdrop = backdrop,
                onHorizontalDragActiveChange = onHorizontalDragActiveChange
            )
            SettingsToggleRow(
                title = "仅在 Wi-Fi 下预加载",
                selected = predictiveEnabled && wifiOnly,
                onSelectedChange = viewModel::setWifiOnlyPrefetch,
                backdrop = backdrop,
                enabled = predictiveEnabled,
                showDivider = false,
                onHorizontalDragActiveChange = onHorizontalDragActiveChange
            )
        }
    }
}
