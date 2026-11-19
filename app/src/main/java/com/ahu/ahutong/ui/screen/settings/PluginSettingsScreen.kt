package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.plugin.PluginManagerSection

@Composable
fun PluginSettingsScreen(onBack: () -> Unit) {
    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = "插件",
            onBack = onBack,
            backdrop = backdrop
        ) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                PluginManagerSection()
            }
        }
    }
}
