package com.ahu.ahutong.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.SettingsActionRow
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

    if (document == PrivacyDocument.HUB) {
        AppPageScaffold(
            title = "隐私政策",
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
            freeContent = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsSection(title = "隐私政策") {
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
                }
            }
        )
        return
    }

    val isAi = document == PrivacyDocument.AI
    AppPageScaffold(
        title = if (isAi) "AI 服务隐私政策" else "安大通隐私政策",
        onBack = {
            if (startWithHub) document = PrivacyDocument.HUB else onBack()
        },
        modifier = Modifier.fillMaxSize(),
        freeContent = {
            AppMarkdown(
                markdown = if (isAi) aiMarkdown.orEmpty() else markdown,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            )
        }
    )
}
