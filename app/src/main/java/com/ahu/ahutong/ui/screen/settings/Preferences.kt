package com.ahu.ahutong.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.UiStyle
import com.ahu.ahutong.notification.CourseReminderCapability
import com.ahu.ahutong.notification.CourseReminderNotifier
import com.ahu.ahutong.notification.CourseReminderScheduler
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsChoice
import com.ahu.ahutong.ui.components.SettingsDialogSelectRow
import com.ahu.ahutong.ui.components.SettingsConfirmationDialog
import com.ahu.ahutong.ui.components.SettingsPageHeader
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.components.SettingsSelectRow
import com.ahu.ahutong.ui.components.SettingsToggleRow
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.PreferencesViewModel

@Composable
fun Preferences(onBack: () -> Unit = {}) {
    val viewModel: PreferencesViewModel = hiltViewModel()
    val context = LocalContext.current
    var isRequestingPermission by remember { mutableStateOf(false) }
    var showClearLearningConfirm by remember { mutableStateOf(false) }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showEnableTrainingContribution by remember { mutableStateOf(false) }
    var showDeleteTrainingContribution by remember { mutableStateOf(false) }
    var isToggleHorizontalDragActive by remember { mutableStateOf(false) }
    val pageScrollState = rememberScrollState()
    val onToggleHorizontalDragActiveChange: (Boolean) -> Unit = { active ->
        isToggleHorizontalDragActive = active
    }

    val appThemeMode by viewModel.appThemeMode.collectAsState()
    val showQRCode by viewModel.showQRCode.collectAsState()
    val useCmbCardRecharge by viewModel.useCmbCardRecharge.collectAsState()
    val uiStyle by viewModel.uiStyle.collectAsState()
    val personalizationEnabled by viewModel.personalizationEnabled.collectAsState()
    val predictivePrefetchEnabled by viewModel.predictivePrefetchEnabled.collectAsState()
    val wifiOnlyPrefetch by viewModel.wifiOnlyPrefetch.collectAsState()
    val behaviorRetentionDays by viewModel.behaviorRetentionDays.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val courseReminderEnabled by viewModel.courseReminderEnabled.collectAsState()
    val courseReminderLiveCountdownEnabled by
        viewModel.courseReminderLiveCountdownEnabled.collectAsState()
    val bootstrapContributionStatus by viewModel.bootstrapContributionStatus.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isRequestingPermission = false
        if (granted) {
            viewModel.setCourseReminderEnabled(true)
            CourseReminderScheduler.reschedule(context)
        } else {
            viewModel.setCourseReminderEnabled(false)
            Toast.makeText(context, "未授予通知权限，无法开启课前提醒", Toast.LENGTH_SHORT).show()
        }
    }

    val requestCourseReminder: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setCourseReminderEnabled(false)
            CourseReminderScheduler.cancel(context)
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!isRequestingPermission) {
                isRequestingPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.setCourseReminderEnabled(true)
            CourseReminderScheduler.reschedule(context)
        }
    }

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    state = pageScrollState,
                    enabled = !isToggleHorizontalDragActive
                )
                .systemBarsPadding()
                .padding(bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            SettingsPageHeader(title = "偏好设置", onBack = onBack, backdrop = backdrop)

            SettingsSection(
                title = "智能体验",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                personalizationEnabled?.let { enabled ->
                    SettingsToggleRow(
                        title = "显示快捷建议",
                        subtitle = "根据本机使用习惯显示常用入口",
                        selected = enabled,
                        onSelectedChange = viewModel::setPersonalizationEnabled,
                        backdrop = backdrop,
                        onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
                    )
                }
                val predictiveEnabled = predictivePrefetchEnabled
                val wifiOnly = wifiOnlyPrefetch
                if (predictiveEnabled != null && wifiOnly != null) {
                    SettingsToggleRow(
                        title = "提前加载预测内容",
                        subtitle = "预测下一步并预加载只读内容",
                        selected = predictiveEnabled,
                        onSelectedChange = viewModel::setPredictivePrefetchEnabled,
                        backdrop = backdrop,
                        onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
                    )
                    SettingsToggleRow(
                        title = "仅在 Wi-Fi 下预加载",
                        selected = predictiveEnabled && wifiOnly,
                        onSelectedChange = viewModel::setWifiOnlyPrefetch,
                        backdrop = backdrop,
                        enabled = predictiveEnabled,
                        onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
                    )
                }
                SettingsDialogSelectRow(
                    title = "本地记录保留期",
                    dialogTitle = "选择本地记录保留期",
                    selected = behaviorRetentionDays,
                    choices = listOf(
                        SettingsChoice(7, "7 天"),
                        SettingsChoice(14, "14 天"),
                        SettingsChoice(30, "30 天")
                    ),
                    onSelected = viewModel::setBehaviorRetentionDays
                )
                SettingsToggleRow(
                    title = "贡献通用模型训练数据",
                    subtitle = if (bootstrapContributionStatus.enabled) {
                        buildString {
                            append("已贡献 ${bootstrapContributionStatus.contributedExamples} 条，待上传 ${bootstrapContributionStatus.pendingExamples} 条")
                            bootstrapContributionStatus.lastUploadAtEpochMs?.let { lastUpload ->
                                append(" · 上次上传 ")
                                append(DateUtils.getRelativeTimeSpanString(lastUpload))
                            }
                        }
                    } else {
                        "独立授权上传去标识化模型就绪样本"
                    },
                    selected = bootstrapContributionStatus.enabled,
                    onSelectedChange = { enabled ->
                        if (enabled) showEnableTrainingContribution = true
                        else showDeleteTrainingContribution = true
                    },
                    backdrop = backdrop,
                    onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
                )
                if (bootstrapContributionStatus.enabled) {
                    SettingsActionRow(
                        title = "删除已上传训练数据",
                        subtitle = "停止贡献并删除当前随机参与者编号下的数据",
                        destructive = true,
                        showChevron = false,
                        onClick = { showDeleteTrainingContribution = true }
                    )
                }
                SettingsActionRow(
                    title = "清除本地学习记录",
                    subtitle = "删除行为统计、训练样本和本地模型",
                    destructive = true,
                    showChevron = false,
                    showDivider = false,
                    onClick = { showClearLearningConfirm = true }
                )
            }

            SettingsSection(
                title = "主页与充值",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
            SettingsToggleRow(
                title = "主页默认显示付款码",
                selected = showQRCode,
                onSelectedChange = viewModel::setShowQRCode,
                backdrop = backdrop,
                onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
            )
            SettingsToggleRow(
                title = "总是使用招商银行充值",
                subtitle = "校园卡充值将直接进入招商银行页面",
                selected = useCmbCardRecharge,
                onSelectedChange = viewModel::setUseCmbCardRecharge,
                backdrop = backdrop,
                showDivider = false,
                onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
            )
        }

            SettingsSection(
                title = "通知",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
            SettingsToggleRow(
                title = "课前提醒",
                subtitle = "上课前 10 分钟提醒下一节课",
                selected = courseReminderEnabled,
                onSelectedChange = requestCourseReminder,
                backdrop = backdrop,
                onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
            )
            SettingsToggleRow(
                title = "课前倒计时岛卡",
                subtitle = if (Build.VERSION.SDK_INT >= 36) {
                    "在支持的系统上显示实时倒计时"
                } else {
                    "当前 Android 版本暂不支持"
                },
                selected = courseReminderLiveCountdownEnabled && Build.VERSION.SDK_INT >= 36,
                onSelectedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT < 36) {
                        Toast.makeText(
                            context,
                            "当前 Android 版本暂不支持岛卡提醒",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        viewModel.setCourseReminderLiveCountdownEnabled(enabled)
                        if (!enabled) CourseReminderNotifier.cancelActiveReminder(context)
                    }
                },
                backdrop = backdrop,
                onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
            )
            SettingsActionRow(
                title = "管理系统岛卡权限",
                showDivider = false,
                onClick = {
                    if (Build.VERSION.SDK_INT < 36) {
                        Toast.makeText(
                            context,
                            "当前 Android 版本暂不支持岛卡提醒",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val promotionIntent =
                            CourseReminderCapability.createPromotionSettingsIntent(context)
                        val fallbackIntent =
                            CourseReminderCapability.createNotificationSettingsIntent(context)
                        runCatching { context.startActivity(promotionIntent) }
                            .getOrElse { context.startActivity(fallbackIntent) }
                    }
                }
            )
        }

            SettingsSection(
                title = "外观",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                SettingsDialogSelectRow(
                    title = "深色模式",
                    dialogTitle = "选择深色模式",
                    selected = appThemeMode,
                    choices = listOf(
                        SettingsChoice(AppThemeMode.FOLLOW_SYSTEM, "跟随系统"),
                        SettingsChoice(AppThemeMode.DARK, "深色"),
                        SettingsChoice(AppThemeMode.LIGHT, "浅色")
                    ),
                    onSelected = viewModel::setAppThemeMode
                )
                SettingsSelectRow(
                    title = "UI 设置",
                    subtitle = "改变整套界面的组件和交互风格",
                    selected = uiStyle,
                    choices = listOf(
                        SettingsChoice(UiStyle.ORIGINAL, "Original"),
                        SettingsChoice(UiStyle.LIQUID_GLASS, "Liquid Glass"),
                        SettingsChoice(UiStyle.RADIANT_UI, "RadiantUI")
                    ),
                    onSelected = viewModel::setUiStyle
                )
                ThemeColorPicker(
                    selectedColor = themeColor,
                    onColorSelected = viewModel::setThemeColor,
                    onCustomColorClick = { showCustomColorDialog = true }
                )
            }
        }
    }

    if (showClearLearningConfirm) {
        SettingsConfirmationDialog(
            title = "清除本地学习记录？",
            message = "行为统计、训练样本、本地模型与晋级状态将被删除，且无法恢复。",
            confirmLabel = "清除",
            destructive = true,
            onConfirm = {
                viewModel.clearPersonalizationLearning()
                showClearLearningConfirm = false
            },
            onDismiss = { showClearLearningConfirm = false }
        )
    }

    if (showEnableTrainingContribution) {
        var includeHistorical by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showEnableTrainingContribution = false },
            title = { Text("贡献通用模型训练数据") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "将上传下一步/多跳预测的数值特征、候选可用性和目标标签，以及参数排序的 16 维候选特征与分级反馈。不会上传账号、设备标识、原始轨迹、完整旅程、设置值、参数内容、presetId 或指纹。随机参与者编号仅用于用户级数据集切分；关闭后会请求删除。"
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeHistorical,
                            onCheckedChange = { includeHistorical = it }
                        )
                        Text("同时贡献最近 30 天已有兼容样本（默认关闭）")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBootstrapTrainingContribution(true, includeHistorical)
                    showEnableTrainingContribution = false
                }) { Text("同意开启") }
            },
            dismissButton = {
                TextButton(onClick = { showEnableTrainingContribution = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteTrainingContribution) {
        SettingsConfirmationDialog(
            title = "停止并删除训练数据？",
            message = "应用会立即停止生成上传样本，删除本机待上传批次，并持续重试服务端删除请求，直到当前随机参与者编号下的数据被删除。",
            confirmLabel = "停止并删除",
            destructive = true,
            onConfirm = {
                viewModel.deleteBootstrapTrainingContribution()
                showDeleteTrainingContribution = false
            },
            onDismiss = { showDeleteTrainingContribution = false }
        )
    }

    if (showCustomColorDialog) {
        CustomThemeColorDialog(
            initialValue = themeColor.orEmpty(),
            onDismiss = { showCustomColorDialog = false },
            onConfirm = { color ->
                viewModel.setThemeColor(color)
                showCustomColorDialog = false
            }
        )
    }
}

private data class ThemeColorChoice(
    val value: String?,
    val name: String,
    val color: Color
)

@Composable
private fun ThemeColorPicker(
    selectedColor: String?,
    onColorSelected: (String?) -> Unit,
    onCustomColorClick: () -> Unit
) {
    val choices = listOf(
        ThemeColorChoice(null, "系统", MaterialTheme.colorScheme.primary),
        ThemeColorChoice("#FF4A90E2", "极光蓝", Color(0xFF4A90E2)),
        ThemeColorChoice("#FFE07A9F", "樱花粉", Color(0xFFE07A9F)),
        ThemeColorChoice("#FFF4A261", "落日橙", Color(0xFFF4A261)),
        ThemeColorChoice("#FF6A994E", "苔藓绿", Color(0xFF6A994E)),
        ThemeColorChoice("#FF9B7EDE", "薰衣草", Color(0xFF9B7EDE)),
        ThemeColorChoice("#FF2E8B57", "翡翠", Color(0xFF2E8B57))
    )
    val presetValues = choices.map { it.value }.toSet()
    val customSelected = selectedColor != null && selectedColor !in presetValues

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "主题色",
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            ThemeColorSwatch(
                name = "自定义",
                color = runCatching {
                    Color(android.graphics.Color.parseColor(selectedColor))
                }.getOrDefault(MaterialTheme.colorScheme.surfaceContainerHighest),
                selected = customSelected,
                custom = true,
                onClick = onCustomColorClick
            )
            choices.forEach { choice ->
                ThemeColorSwatch(
                    name = choice.name,
                    color = choice.color,
                    selected = selectedColor == choice.value,
                    onClick = { onColorSelected(choice.value) }
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSwatch(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    custom: Boolean = false
) {
    Column(
        modifier = Modifier
            .clip(SmoothRoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(SmoothRoundedCornerShape(16.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            when {
                selected -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "已选择",
                    tint = Color.White
                )
                custom -> Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "自定义主题色",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = name,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CustomThemeColorDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val valid = remember(value) {
        runCatching { android.graphics.Color.parseColor(value) }.isSuccess
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义主题色") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("ARGB Hex") },
                placeholder = { Text("#FF007FAC") },
                isError = value.isNotBlank() && !valid,
                supportingText = {
                    if (value.isNotBlank() && !valid) Text("请输入有效的颜色代码")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(value) }
            ) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
