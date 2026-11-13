package com.ahu.ahutong.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.R
import com.ahu.ahutong.assistant.AssistantAccessState
import com.ahu.ahutong.assistant.AssistantPreferences
import com.ahu.ahutong.assistant.model.AssistantMessage
import com.ahu.ahutong.assistant.model.AssistantModelConfig
import com.ahu.ahutong.assistant.model.AssistantModelLibrary
import com.ahu.ahutong.assistant.model.AssistantModelProfile
import com.ahu.ahutong.assistant.model.AssistantThinkingLevel
import com.ahu.ahutong.assistant.model.OpenAiCompatibleChatModel
import com.ahu.ahutong.assistant.model.guessSupportsThinking
import com.ahu.ahutong.assistant.model.guessSupportsVision
import com.ahu.ahutong.assistant.model.providerLabelFor
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsConfirmationDialog
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.components.SettingsToggleRow
import com.ahu.ahutong.ui.glow.AssistantGlowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 设置：模型库（多套 OpenAI 兼容配置）+ 服务开关 + 联网搜索。
 * 首条添加的模型默认使用；在输入框里切换是全局的。
 */
@Composable
fun AiSettings(
    onBack: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by AssistantModelLibrary.state.collectAsState()
    val active = snapshot.active
    var editor by remember { mutableStateOf<AssistantModelProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AssistantModelProfile?>(null) }
    var testingId by remember { mutableStateOf<String?>(null) }
    var showClear by remember { mutableStateOf(false) }
    var showPrivacySummary by remember { mutableStateOf(false) }

    var searchEnabled by remember { mutableStateOf(AssistantPreferences.searchEnabled()) }
    var serviceEnabled by remember { mutableStateOf(AssistantPreferences.serviceEnabled()) }

    val supportsSearch = active?.baseUrl?.contains("deepseek.com", ignoreCase = true) == true
    LaunchedEffect(active?.id, supportsSearch) {
        if (!supportsSearch) {
            searchEnabled = false
            AssistantPreferences.setSearchEnabled(false)
        }
    }

    fun testProfile(profile: AssistantModelProfile) {
        if (testingId != null) return
        val key = AssistantModelLibrary.apiKey(profile.id)
        if (key.isNullOrBlank()) {
            Toast.makeText(context, "该模型缺少 API Key", Toast.LENGTH_SHORT).show()
            return
        }
        testingId = profile.id
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val chat = OpenAiCompatibleChatModel(
                        config = AssistantModelConfig(
                            baseUrl = profile.baseUrl,
                            model = profile.model,
                            apiKey = key,
                        ).copy(
                            supportsThinking = profile.supportsThinking,
                            supportsVision = profile.supportsVision,
                        ),
                        thinkingLevel = AssistantThinkingLevel.OFF,
                        maxTokens = 16,
                    )
                    val reply = chat.complete(
                        messages = listOf(
                            AssistantMessage(role = "system", content = "只回复：连接成功"),
                            AssistantMessage(role = "user", content = "ping"),
                        ),
                        tools = emptyList(),
                    )
                    reply.content?.takeIf { it.isNotBlank() } ?: "连接成功"
                }
            }
            testingId = null
            Toast.makeText(
                context,
                result.getOrElse { "连接失败：${it.message ?: it.javaClass.simpleName}" }.take(120),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun markSaved() {
        if (!AssistantPreferences.aiPrivacySummarySeen()) showPrivacySummary = true
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
                title = "模型",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop,
            ) {
                if (snapshot.profiles.isEmpty()) {
                    Text(
                        text = "还没有配置模型。添加一套 OpenAI 兼容接口后即可使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                } else {
                    snapshot.profiles.forEachIndexed { index, profile ->
                        val isActive = profile.id == active?.id
                        ModelLibraryRow(
                            profile = profile,
                            isActive = isActive,
                            testing = testingId == profile.id,
                            showDivider = index < snapshot.profiles.lastIndex,
                            onActivate = {
                                if (!isActive) AssistantModelLibrary.setActive(profile.id)
                            },
                            onEdit = { editor = profile },
                            onTest = { testProfile(profile) },
                            onDelete = { deleting = profile },
                        )
                    }
                }
                SettingsActionRow(
                    title = "添加模型",
                    subtitle = "支持任意 OpenAI 兼容接口；首个添加的模型默认使用",
                    leadingIcon = Icons.Outlined.Add,
                    showChevron = false,
                    showDivider = false,
                    onClick = { adding = true },
                )
            }

            if (supportsSearch) {
                SettingsSection(
                    title = "联网搜索",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    backdrop = backdrop,
                ) {
                    SettingsToggleRow(
                        title = "允许联网搜索",
                        subtitle = "需要实时信息时通过 DeepSeek 官方搜索获取网页结果",
                        selected = searchEnabled,
                        backdrop = backdrop,
                        showDivider = false,
                        onSelectedChange = {
                            searchEnabled = it
                            AssistantPreferences.setSearchEnabled(it)
                        },
                    )
                }
            }

            if (snapshot.profiles.isNotEmpty()) {
                SettingsSection(
                    title = "维护",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    backdrop = backdrop,
                ) {
                    SettingsActionRow(
                        title = "清除全部模型",
                        subtitle = "删除本机保存的所有 Base URL、模型名与 API Key",
                        destructive = true,
                        showChevron = false,
                        showDivider = false,
                        onClick = { showClear = true },
                    )
                }
            }
        }
    }

    if (adding) {
        ModelEditorDialog(
            initial = null,
            existingKey = null,
            onDismiss = { adding = false },
            onSaved = {
                adding = false
                markSaved()
            },
        )
    }

    editor?.let { profile ->
        ModelEditorDialog(
            initial = profile,
            existingKey = AssistantModelLibrary.apiKey(profile.id),
            onDismiss = { editor = null },
            onSaved = {
                editor = null
                markSaved()
            },
        )
    }

    deleting?.let { profile ->
        SettingsConfirmationDialog(
            title = "删除模型？",
            message = "将删除「${profile.displayName}」及其本机保存的 API Key。" +
                if (profile.id == active?.id) "它是当前使用的模型，删除后将回退到列表中的第一条。" else "",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                AssistantModelLibrary.delete(profile.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    if (showClear) {
        SettingsConfirmationDialog(
            title = "清除全部模型？",
            message = "清除后需要重新添加模型才能再次使用 AI 功能；对话历史不会删除。",
            confirmLabel = "清除",
            destructive = true,
            onConfirm = {
                AssistantModelLibrary.clear()
                showClear = false
                Toast.makeText(context, "已清除全部模型", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClear = false },
        )
    }

    if (showPrivacySummary) {
        val dismissSummary = {
            showPrivacySummary = false
            AssistantPreferences.markAiPrivacySummarySeen()
        }
        AppDialog(
            title = "AI 服务使用须知",
            subtitle = "首次保存配置时，向你说明数据会去哪里",
            onDismiss = dismissSummary,
            contentScrollable = true,
            titleStyle = MaterialTheme.typography.headlineSmall,
            actions = listOf(
                AppDialogAction(
                    label = "查看完整政策",
                    onClick = {
                        dismissSummary()
                        onOpenPrivacyPolicy()
                    },
                ),
                AppDialogAction(
                    label = "我知道了",
                    onClick = dismissSummary,
                    style = AppDialogActionStyle.Primary,
                ),
            ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                AiSummaryLine("对话内容、最近 24 条历史与本轮必要的工具结果，会发送到你配置的模型服务。")
                AiSummaryLine("课表、成绩、余额、账单、邮件等工具在学校系统执行；结果摘要可能随请求发送给模型。")
                AiSummaryLine("写操作会先弹确认卡；校园卡支付密码只在本机使用，不保存、不发送给模型。")
                AiSummaryLine("对话历史保存在本机，可在历史页逐段删除；清除模型不会删除历史。")
                AiSummaryLine("AI 回答可能不准确，请以学校系统和业务页面为准。")
            }
        }
    }
}

@Composable
private fun ModelLibraryRow(
    profile: AssistantModelProfile,
    isActive: Boolean,
    testing: Boolean,
    showDivider: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onActivate)
                .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_assistant_sparkle_fill),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        CapabilityTag("使用中", colors.primary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        profile.providerLabel.ifBlank { "自定义" },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (profile.supportsVision) {
                        CapabilityTag("视觉", colors.primary)
                    }
                    if (profile.supportsThinking) {
                        CapabilityTag("思考", colors.primary)
                    }
                    if (!profile.supportsVision && !profile.supportsThinking) {
                        CapabilityTag("文本", colors.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onTest,
                modifier = Modifier.size(36.dp),
                enabled = !testing,
            ) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = "测试联通",
                    tint = if (testing) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = colors.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 44.dp, end = 16.dp),
                color = colors.outlineVariant.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun CapabilityTag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ModelEditorDialog(
    initial: AssistantModelProfile?,
    existingKey: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf(initial?.name.orEmpty()) }
    var baseUrl by remember {
        mutableStateOf(initial?.baseUrl?.takeIf { it.isNotBlank() } ?: AssistantPreferences.DEFAULT_BASE_URL)
    }
    var modelName by remember {
        mutableStateOf(initial?.model?.takeIf { it.isNotBlank() } ?: AssistantPreferences.DEFAULT_MODEL)
    }
    var providerLabel by remember { mutableStateOf(initial?.providerLabel.orEmpty()) }
    var supportsVision by remember { mutableStateOf(initial?.supportsVision ?: false) }
    var supportsThinking by remember { mutableStateOf(initial?.supportsThinking ?: true) }
    var apiKey by remember { mutableStateOf("") }
    var capabilitiesTouched by remember { mutableStateOf(initial != null) }
    var labelTouched by remember { mutableStateOf(initial?.providerLabel?.isNotBlank() == true) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(baseUrl, modelName) {
        if (!capabilitiesTouched) {
            supportsVision = guessSupportsVision(baseUrl, modelName)
            supportsThinking = guessSupportsThinking(baseUrl, modelName)
        }
        if (!labelTouched) providerLabel = providerLabelFor(baseUrl, modelName)
    }

    // 校验必须在点击时读取当前输入；不要在组合阶段缓存成 val，否则会拿到旧值。
    fun currentInput(): Triple<String, String, String> =
        Triple(baseUrl.trim(), modelName.trim(), apiKey.trim().takeIf { it.isNotBlank() } ?: existingKey.orEmpty())

    fun save() {
        val (url, model, key) = currentInput()
        val secure = AssistantModelConfig(baseUrl = url, model = "placeholder", apiKey = "placeholder").isSecureEndpoint
        error = when {
            !secure -> "Base URL 必须使用 HTTPS（本机开发地址除外）"
            url.isBlank() -> "请填写 Base URL"
            model.isBlank() -> "请填写模型名"
            key.isBlank() -> "请填写 API Key"
            else -> null
        }
        if (error != null) return
        val profile = (initial ?: AssistantModelProfile()).copy(
            name = displayName.trim().ifBlank { model },
            baseUrl = url,
            model = model,
            providerLabel = providerLabel.trim().ifBlank { providerLabelFor(url, model) },
            supportsVision = supportsVision,
            supportsThinking = supportsThinking,
        )
        val ok = if (initial == null) {
            AssistantModelLibrary.add(profile, key) != null
        } else {
            AssistantModelLibrary.update(profile, apiKey.trim().takeIf { it.isNotBlank() })
        }
        if (!ok) {
            error = "保存失败，无法访问本机安全存储"
            return
        }
        Toast.makeText(context, "模型已保存", Toast.LENGTH_SHORT).show()
        onSaved()
    }

    fun test() {
        if (testing) return
        val (url, model, key) = currentInput()
        val secure = AssistantModelConfig(baseUrl = url, model = "placeholder", apiKey = "placeholder").isSecureEndpoint
        error = when {
            !secure -> "Base URL 必须使用 HTTPS（本机开发地址除外）"
            url.isBlank() -> "请填写 Base URL"
            model.isBlank() -> "请填写模型名"
            key.isBlank() -> "请填写 API Key"
            else -> null
        }
        if (error != null) return
        testing = true
        status = "正在连接…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val chat = OpenAiCompatibleChatModel(
                        config = AssistantModelConfig(
                            baseUrl = url,
                            model = model,
                            apiKey = key,
                        ).copy(supportsThinking = supportsThinking, supportsVision = supportsVision),
                        thinkingLevel = AssistantThinkingLevel.OFF,
                        maxTokens = 16,
                    )
                    val reply = chat.complete(
                        messages = listOf(
                            AssistantMessage(role = "system", content = "只回复：连接成功"),
                            AssistantMessage(role = "user", content = "ping"),
                        ),
                        tools = emptyList(),
                    )
                    reply.content?.takeIf { it.isNotBlank() } ?: "连接成功（模型未返回文本）"
                }
            }
            testing = false
            status = result.getOrElse { error ->
                "连接失败：" + (error.message ?: error.javaClass.simpleName)
            }.take(160)
        }
    }

    AppDialog(
        title = if (initial == null) "添加模型" else "编辑模型",
        subtitle = "OpenAI 兼容接口；Key 只保存在本机 Keystore",
        onDismiss = onDismiss,
        contentScrollable = true,
        actions = listOf(
            AppDialogAction(label = "取消", onClick = onDismiss),
            AppDialogAction(label = "保存", onClick = ::save, style = AppDialogActionStyle.Primary),
        ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = "显示名称（可选）",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
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
                label = if (existingKey.isNullOrBlank()) "API Key" else "API Key（已保存，留空不修改）",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
            )
            AppTextField(
                value = providerLabel,
                onValueChange = {
                    providerLabel = it
                    labelTouched = true
                },
                label = "来源标签",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            CapabilitySwitch(
                title = "支持图片输入",
                subtitle = "用于后续“看题”等视觉能力",
                checked = supportsVision,
                onCheckedChange = {
                    supportsVision = it
                    capabilitiesTouched = true
                },
            )
            CapabilitySwitch(
                title = "支持思考强度",
                subtitle = "关闭后请求不会携带 thinking / reasoning_effort",
                checked = supportsThinking,
                onCheckedChange = {
                    supportsThinking = it
                    capabilitiesTouched = true
                },
            )
            error?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(
                    onClick = ::test,
                    modifier = Modifier.weight(1f),
                    enabled = !testing,
                    variant = AppButtonVariant.Secondary,
                ) {
                    Text(if (testing) "连接中…" else "测试联通")
                }
            }
            status?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (text.startsWith("连接失败") || text.startsWith("正在连接")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Text(
                text = "测试会发送一条最小真实请求（最多 16 token），不计入对话额度。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CapabilitySwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.size(4.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AiSummaryLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
