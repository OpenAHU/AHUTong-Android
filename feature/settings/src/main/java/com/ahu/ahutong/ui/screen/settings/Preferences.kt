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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.model.DEFAULT_THEME_COLOR
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsChoice
import com.ahu.ahutong.ui.components.SettingsConfirmationDialog
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.SettingsSelectRow
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.components.SettingsToggleRow
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.PreferencesViewModel

@Composable
fun Preferences(onBack: () -> Unit = {}, onOpenThemeLab: () -> Unit = {}) {
    val viewModel: PreferencesViewModel = hiltViewModel()
    val context = LocalContext.current
    var isRequestingPermission by remember { mutableStateOf(false) }
    var showClearLearningConfirm by remember { mutableStateOf(false) }
    var showEnableTrainingContribution by remember { mutableStateOf(false) }
    var showDeleteTrainingContribution by remember { mutableStateOf(false) }
    var isToggleHorizontalDragActive by remember { mutableStateOf(false) }
    val pageScrollState = rememberScrollState()
    val onToggleHorizontalDragActiveChange: (Boolean) -> Unit = { active ->
        isToggleHorizontalDragActive = active
    }

    val showQRCode by viewModel.showQRCode.collectAsState()
    val personalizationEnabled by viewModel.personalizationEnabled.collectAsState()
    val predictivePrefetchEnabled by viewModel.predictivePrefetchEnabled.collectAsState()
    val wifiOnlyPrefetch by viewModel.wifiOnlyPrefetch.collectAsState()
    val behaviorRetentionDays by viewModel.behaviorRetentionDays.collectAsState()
    val useBuiltInSecurePasswordKeyboard by
        viewModel.useBuiltInSecurePasswordKeyboard.collectAsState()
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
            viewModel.rescheduleCourseReminders()
        } else {
            viewModel.setCourseReminderEnabled(false)
            Toast.makeText(context, "未授予通知权限，无法开启课前提醒", Toast.LENGTH_SHORT).show()
        }
    }

    val requestCourseReminder: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setCourseReminderEnabled(false)
            viewModel.cancelCourseReminders()
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
            viewModel.rescheduleCourseReminders()
        }
    }

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = "偏好设置",
            onBack = onBack,
            backdrop = backdrop,
            scrollState = pageScrollState,
            scrollEnabled = !isToggleHorizontalDragActive
        ) {
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
                SettingsSelectRow(
                    title = "本地记录保留期",
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
                title = "使用内置安全密码键盘",
                subtitle = "关闭后使用系统密码键盘",
                selected = useBuiltInSecurePasswordKeyboard,
                onSelectedChange = viewModel::setUseBuiltInSecurePasswordKeyboard,
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
                        if (!enabled) viewModel.dismissActiveCourseReminder()
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
                        viewModel.openCourseReminderSystemSettings()
                    }
                }
            )
        }

            ThemeSettingsSection(
                viewModel = viewModel,
                backdrop = backdrop,
                onOpenDetails = onOpenThemeLab
            )
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
        AppDialog(
            title = "贡献通用模型训练数据",
            onDismiss = { showEnableTrainingContribution = false },
            contentSpacing = 12.dp,
            actions = listOf(
                AppDialogAction(
                    "取消",
                    onClick = { showEnableTrainingContribution = false }
                ),
                AppDialogAction(
                    "同意开启",
                    onClick = {
                        viewModel.setBootstrapTrainingContribution(true, includeHistorical)
                        showEnableTrainingContribution = false
                    },
                    style = AppDialogActionStyle.Primary
                )
            ),
            content = {
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
}
