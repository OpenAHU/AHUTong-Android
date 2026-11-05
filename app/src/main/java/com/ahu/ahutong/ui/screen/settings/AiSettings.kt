package com.ahu.ahutong.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.assistant.AssistantAccessState
import com.ahu.ahutong.assistant.AssistantPersonality
import com.ahu.ahutong.assistant.AssistantPreferences
import com.ahu.ahutong.assistant.model.AssistantMessage
import com.ahu.ahutong.assistant.model.AssistantModelConfig
import com.ahu.ahutong.assistant.model.AssistantModelConfigStore
import com.ahu.ahutong.assistant.model.OpenAiCompatibleChatModel
import com.ahu.ahutong.ui.glow.AssistantGlowState
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsChoice
import com.ahu.ahutong.ui.components.SettingsConfirmationDialog
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.components.SettingsSelectRow
import com.ahu.ahutong.ui.components.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 模型自配置入口：OpenAI 兼容接口（Base URL + 模型名 + API Key）。
 * Key 与支付码缓存共用 Android Keystore，不落明文、不进日志。
 */
@Composable
fun AiSettings(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stored by remember {
        mutableStateOf(runCatching { AssistantModelConfigStore.load() }.getOrNull())
    }
    var baseUrl by remember {
        mutableStateOf(stored?.baseUrl?.takeIf { it.isNotBlank() } ?: AssistantPreferences.DEFAULT_BASE_URL)
    }
    var modelName by remember {
        mutableStateOf(stored?.model?.takeIf { it.isNotBlank() } ?: AssistantPreferences.DEFAULT_MODEL)
    }
    var personality by remember { mutableStateOf(AssistantPreferences.personality()) }
    var searchEnabled by remember { mutableStateOf(AssistantPreferences.searchEnabled()) }
    var serviceEnabled by remember { mutableStateOf(AssistantPreferences.serviceEnabled()) }
    val secureEndpoint = AssistantModelConfig(
        baseUrl = baseUrl.trim(),
        model = "placeholder",
        apiKey = "placeholder",
    ).isSecureEndpoint
    val supportsSearch = secureEndpoint && baseUrl.contains("deepseek.com", ignoreCase = true)
    var apiKey by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showClear by remember { mutableStateOf(false) }

    fun keyForSave(): String? = apiKey.trim().takeIf { it.isNotBlank() } ?: stored?.apiKey

    fun save() {
        val key = keyForSave()
        if (!secureEndpoint) {
            Toast.makeText(context, "Base URL 必须使用 HTTPS（本机开发地址除外）", Toast.LENGTH_SHORT).show()
            return
        }
        if (baseUrl.isBlank() || modelName.isBlank() || key.isNullOrBlank()) {
            Toast.makeText(context, "请填写 Base URL、模型名和 API Key", Toast.LENGTH_SHORT).show()
            return
        }
        val saved = runCatching {
            AssistantModelConfigStore.save(
                AssistantModelConfig(
                    baseUrl = baseUrl.trim(),
                    model = modelName.trim(),
                    apiKey = key,
                )
            )
        }
        if (saved.isFailure) {
            Toast.makeText(context, "保存失败，无法访问本机安全存储", Toast.LENGTH_SHORT).show()
            return
        }
        stored = runCatching { AssistantModelConfigStore.load() }.getOrNull()
        if (!supportsSearch) {
            searchEnabled = false
            AssistantPreferences.setSearchEnabled(false)
        }
        apiKey = ""
        status = "已保存，AI 入口将使用该模型"
        Toast.makeText(context, "AI 设置已保存", Toast.LENGTH_SHORT).show()
    }

    fun testConnection() {
        val key = keyForSave()
        if (!secureEndpoint) {
            Toast.makeText(context, "Base URL 必须使用 HTTPS（本机开发地址除外）", Toast.LENGTH_SHORT).show()
            return
        }
        if (baseUrl.isBlank() || modelName.isBlank() || key.isNullOrBlank()) {
            Toast.makeText(context, "请先填写完整配置", Toast.LENGTH_SHORT).show()
            return
        }
        if (testing) return
        testing = true
        status = "正在连接…"
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val chat = OpenAiCompatibleChatModel(
                        AssistantModelConfig(
                            baseUrl = baseUrl.trim(),
                            model = modelName.trim(),
                            apiKey = key,
                        )
                    )
                    val reply = chat.complete(
                        messages = listOf(
                            AssistantMessage(role = "system", content = "只回复：连接成功"),
                            AssistantMessage(role = "user", content = "ping"),
                        ),
                        tools = emptyList(),
                    )
                    reply.content?.takeIf { it.isNotBlank() }
                        ?: "连接成功（模型未返回文本）"
                }
            }
            if (!supportsSearch) {
                searchEnabled = false
                AssistantPreferences.setSearchEnabled(false)
            }
            testing = false
            status = outcome.getOrElse { error ->
                "连接失败：" + (error.message ?: error.javaClass.simpleName)
            }.take(160)
        }
    }

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = "AI 设置",
            onBack = onBack,
            backdrop = backdrop,
        ) {
            SettingsSection(
                title = "AI 服务",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop,
            ) {
                SettingsToggleRow(
                    title = "开启 AI 服务",
                    subtitle = if (serviceEnabled) {
                        "主页显示 AHUTong Intelligence 入口"
                    } else {
                        "主页隐藏 AI 入口，已打开的 AI 层会关闭"
                    },
                    selected = serviceEnabled,
                    backdrop = backdrop,
                    showDivider = false,
                    onSelectedChange = { enabled ->
                        serviceEnabled = enabled
                        AssistantPreferences.setServiceEnabled(enabled)
                        AssistantAccessState.refresh()
                        if (!enabled) AssistantGlowState.visible = false
                    },
                )
            }

            SettingsSection(
                title = "模型服务",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "OpenAI 兼容接口。留空 API Key 表示沿用已保存的 Key。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AppTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = "Base URL",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    AppTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = "模型名",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    AppTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = if (stored?.apiKey.isNullOrBlank()) {
                            "API Key"
                        } else {
                            "API Key（已保存，留空不修改）"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppButton(onClick = ::save, modifier = Modifier.weight(1f)) {
                            Text("保存")
                        }
                        AppButton(
                            onClick = ::testConnection,
                            modifier = Modifier.weight(1f),
                            enabled = !testing,
                            variant = AppButtonVariant.Secondary,
                        ) {
                            Text(if (testing) "连接中…" else "测试连接")
                        }
                    }
                    status?.let { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (text.startsWith("连接失败") || text.startsWith("连接中")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }

            SettingsSection(
                title = "助手性格",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop,
            ) {
                SettingsSelectRow(
                    title = "性格",
                    selected = personality,
                    choices = AssistantPersonality.entries.map {
                        SettingsChoice(it, it.label)
                    },
                    showDivider = false,
                    onSelected = {
                        personality = it
                        AssistantPreferences.setPersonality(it)
                        status = "已切换为${it.label}"
                    },
                )
            }

            SettingsSection(
                title = "联网搜索",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop,
            ) {
                SettingsToggleRow(
                    title = "允许联网搜索",
                    subtitle = if (supportsSearch) {
                        "需要实时信息时通过 DeepSeek 官方搜索获取网页结果"
                    } else {
                        "当前接口暂不支持联网搜索；目前仅 DeepSeek 官方接口可用"
                    },
                    selected = searchEnabled && supportsSearch,
                    enabled = supportsSearch,
                    backdrop = backdrop,
                    showDivider = false,
                    onSelectedChange = {
                        searchEnabled = it
                        AssistantPreferences.setSearchEnabled(it)
                    },
                )
            }

            SettingsSection(
                title = "数据说明",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop,
            ) {
                Text(
                    text = "启用模型后，你的问题以及工具返回的文本（课表、成绩、余额、账单、邮件摘要等）" +
                        "会作为上下文发送到所填模型服务；写操作会先弹确认卡，" +
                        "支付密码只在本机确认时使用、不进入模型上下文。仅在信任该服务时启用。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (stored != null) {
                SettingsSection(
                    title = "维护",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    backdrop = backdrop,
                ) {
                    SettingsActionRow(
                        title = "清除模型配置",
                        subtitle = "删除本机保存的 Base URL、模型名与 API Key",
                        destructive = true,
                        showChevron = false,
                        showDivider = false,
                        onClick = { showClear = true },
                    )
                }
            }
        }
    }

    if (showClear) {
        SettingsConfirmationDialog(
            title = "清除模型配置？",
            message = "清除后需要重新填写模型配置才能再次使用 AI 功能。",
            confirmLabel = "清除",
            destructive = true,
            onConfirm = {
                runCatching { AssistantModelConfigStore.clear() }
                stored = null
                baseUrl = ""
                modelName = ""
                apiKey = ""
                status = null
                showClear = false
                Toast.makeText(context, "已清除模型配置", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClear = false },
        )
    }
}
