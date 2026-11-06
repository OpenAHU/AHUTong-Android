package com.ahu.ahutong.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.markdown.AppMarkdown

private enum class PrivacyDocument { HUB, GENERAL, AI }

/**
 * 隐私政策阅读页。`startWithHub=true` 时先展示两个入口：
 * 安大通隐私政策与 AI 服务隐私政策；两者共用同一套 Markdown 渲染。
 */
@Composable
fun PrivacyPolicyScreen(
    markdown: String,
    onBack: () -> Unit,
    title: String = "隐私政策",
    aiMarkdown: String? = null,
    startWithHub: Boolean = false,
) {
    var document by rememberSaveable {
        mutableStateOf(if (startWithHub) PrivacyDocument.HUB else PrivacyDocument.GENERAL)
    }
    BackHandler(enabled = startWithHub && document != PrivacyDocument.HUB) {
        document = PrivacyDocument.HUB
    }

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = when (document) {
                PrivacyDocument.HUB -> "隐私政策"
                PrivacyDocument.GENERAL -> if (startWithHub) "安大通隐私政策" else title
                PrivacyDocument.AI -> "AI 服务隐私政策"
            },
            onBack = {
                if (startWithHub && document != PrivacyDocument.HUB) {
                    document = PrivacyDocument.HUB
                } else {
                    onBack()
                }
            },
            backdrop = backdrop,
        ) {
            if (document == PrivacyDocument.HUB) {
                SettingsSection(title = "隐私政策", backdrop = backdrop) {
                    SettingsActionRow(
                        title = "安大通隐私政策",
                        subtitle = "账号、校园业务、设备权限与第三方服务",
                        onClick = { document = PrivacyDocument.GENERAL },
                    )
                    SettingsActionRow(
                        title = "AI 服务隐私政策",
                        subtitle = "模型调用、对话历史与工具数据",
                        showDivider = false,
                        onClick = { document = PrivacyDocument.AI },
                    )
                }
            } else {
                AppMarkdown(
                    markdown = if (document == PrivacyDocument.AI) {
                        aiMarkdown.orEmpty()
                    } else {
                        markdown
                    },
                    lazy = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}
