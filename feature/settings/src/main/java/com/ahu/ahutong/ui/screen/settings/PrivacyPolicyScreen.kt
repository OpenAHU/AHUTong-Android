package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.markdown.AppMarkdown

/** 隐私政策目录：安大通总政策与 AI 服务政策两个入口。 */
@Composable
fun PrivacyPolicyHubScreen(
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenAi: () -> Unit,
) {
    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = "隐私政策",
            onBack = onBack,
            backdrop = backdrop,
        ) {
            SettingsSection(
                title = "隐私政策",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop,
            ) {
                SettingsActionRow(
                    title = "安大通隐私政策",
                    subtitle = "账号、校园业务、设备权限与第三方服务",
                    onClick = onOpenGeneral,
                )
                SettingsActionRow(
                    title = "AI 服务隐私政策",
                    subtitle = "模型调用、对话历史与工具数据",
                    showDivider = false,
                    onClick = onOpenAi,
                )
            }
        }
    }
}

/** 单篇隐私政策全文。 */
@Composable
fun PrivacyPolicyScreen(
    markdown: String,
    onBack: () -> Unit,
    title: String,
) {
    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = title,
            onBack = onBack,
            backdrop = backdrop,
        ) {
            AppMarkdown(
                markdown = markdown,
                lazy = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }
}
