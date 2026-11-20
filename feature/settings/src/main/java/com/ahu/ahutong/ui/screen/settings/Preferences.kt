package com.ahu.ahutong.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.ahu.ahutong.ui.haptics.HapticCheckbox as Checkbox
import com.ahu.ahutong.ui.haptics.AppHapticEvent
import com.ahu.ahutong.ui.haptics.rememberAppHapticFeedback
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.kyant.backdrop.Backdrop

enum class PreferencesSection(
    val routeKey: String,
    val title: String,
    val subtitle: String,
) {
    APPEARANCE("appearance", "外观与主题", "主题套装、深色模式、主题色、背景与触感"),
    HOME("home", "主页设置", "付款码与主页卡片"),
    NOTIFICATIONS("notifications", "通知提醒", "课前提醒、准时权限与岛卡"),
    INTELLIGENCE("intelligence", "智能体验", "快捷建议、预加载与本地学习"),
    DATA("data", "数据与隐私", "本地记录、必吃榜共建与训练数据");

    companion object {
        fun fromRouteKey(value: String?): PreferencesSection? =
            entries.firstOrNull { it.routeKey == value }
    }
}

@Composable
fun Preferences(
    section: PreferencesSection,
    onBack: () -> Unit = {},
    onOpenThemeLab: () -> Unit = {},
    onOpenThemeColors: () -> Unit = {},
    onOpenElectricityAlertSettings: () -> Unit = {},
    undergraduateEnabled: Boolean = true
) {
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
    val heroPriority by viewModel.heroPriority.collectAsState()
    val personalizationEnabled by viewModel.personalizationEnabled.collectAsState()
    val canteenUploadConsent by viewModel.canteenUploadConsent.collectAsState()
    val predictivePrefetchEnabled by viewModel.predictivePrefetchEnabled.collectAsState()
    val wifiOnlyPrefetch by viewModel.wifiOnlyPrefetch.collectAsState()
    val behaviorRetentionDays by viewModel.behaviorRetentionDays.collectAsState()
    val useBuiltInSecurePasswordKeyboard by
        viewModel.useBuiltInSecurePasswordKeyboard.collectAsState()
    val courseReminderEnabled by viewModel.courseReminderEnabled.collectAsState()
    val courseReminderExactAlarmAllowed by viewModel.courseReminderExactAlarmAllowed.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCourseReminderAlarmAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.refreshCourseReminderAlarmAccess()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val courseReminderLiveCountdownEnabled by
        viewModel.courseReminderLiveCountdownEnabled.collectAsState()
    val xuexiaotongRemind by viewModel.xuexiaotongRemind.collectAsState()
    val bootstrapContributionStatus by viewModel.bootstrapContributionStatus.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isRequestingPermission = false
        if (granted) {
            viewModel.setCourseReminderEnabled(true)
        } else {
            viewModel.setCourseReminderEnabled(false)
            Toast.makeText(context, "未授予通知权限，无法开启课前提醒", Toast.LENGTH_SHORT).show()
        }
    }

    val requestCourseReminder: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setCourseReminderEnabled(false)
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
        }
    }

    val xuexiaotongPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isRequestingPermission = false
        if (granted) {
            viewModel.setXuexiaotongRemindEnabled(true)
        } else {
            Toast.makeText(context, "未授予通知权限，无法开启作业提醒", Toast.LENGTH_SHORT).show()
        }
    }

    val requestXuexiaotongReminder: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setXuexiaotongRemindEnabled(false)
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!isRequestingPermission) {
                isRequestingPermission = true
                xuexiaotongPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.setXuexiaotongRemindEnabled(true)
        }
    }

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = section.title,
            onBack = onBack,
            backdrop = backdrop,
            scrollState = pageScrollState,
            scrollEnabled = !isToggleHorizontalDragActive
        ) {
            LaunchedEffect(section) { pageScrollState.scrollTo(0) }
            if (section == PreferencesSection.APPEARANCE) ThemeSettingsSection(
                viewModel = viewModel,
                backdrop = backdrop,
                onOpenDetails = onOpenThemeLab,
                onOpenThemeColors = onOpenThemeColors,
                onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
            )

            if (section == PreferencesSection.HOME) SettingsSection(
                title = "校园卡显示",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                SettingsToggleRow(
                    title = "付款码常开",
                    selected = showQRCode,
                    onSelectedChange = viewModel::setShowQRCode,
                    backdrop = backdrop,
                    showDivider = false,
                    onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
                )
            }

            if (section == PreferencesSection.HOME) SettingsSection(
                title = "主标题优先级",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                HeroPriorityList(
                    order = heroPriority,
                    onCommit = viewModel::setHeroPriority
                )
            }

            if (section == PreferencesSection.NOTIFICATIONS && undergraduateEnabled) SettingsSection(
                title = "电费预警",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                SettingsActionRow(
                    title = "电费预警",
                    showDivider = false,
                    onClick = onOpenElectricityAlertSettings
                )
            }

            if (section == PreferencesSection.NOTIFICATIONS && undergraduateEnabled) SettingsSection(
                title = "通知",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                SettingsToggleRow(
                    title = "课前提醒",
                    selected = courseReminderEnabled,
                    onSelectedChange = requestCourseReminder,
                    backdrop = backdrop,
                    onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsActionRow(
                        title = "准时提醒权限",
                        subtitle = if (courseReminderExactAlarmAllowed) {
                            "已允许预约准时提醒"
                        } else {
                            "未允许，课前提醒可能延迟；点击前往授权"
                        },
                        onClick = { viewModel.openCourseReminderExactAlarmSettings() }
                    )
                }
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

            if (section == PreferencesSection.NOTIFICATIONS && undergraduateEnabled) SettingsSection(
                title = "学习通作业提醒",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                SettingsToggleRow(
                    title = "作业提醒",
                    selected = xuexiaotongRemind.enabled,
                    onSelectedChange = requestXuexiaotongReminder,
                    backdrop = backdrop,
                    showDivider = xuexiaotongRemind.enabled,
                    onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
                )
                if (xuexiaotongRemind.enabled) {
                    SettingsSelectRow(
                        title = "提前提醒",
                        selected = xuexiaotongRemind.leadMinutes,
                        choices = listOf(
                            SettingsChoice(0, "截止时"),
                            SettingsChoice(60, "提前1小时"),
                            SettingsChoice(360, "提前6小时"),
                            SettingsChoice(720, "提前12小时"),
                            SettingsChoice(1440, "提前1天")
                        ),
                        onSelected = viewModel::setXuexiaotongRemindLeadMinutes
                    )
                    SettingsToggleRow(
                        title = "仅提醒未完成作业",
                        selected = xuexiaotongRemind.onlyTodo,
                        onSelectedChange = viewModel::setXuexiaotongRemindOnlyTodo,
                        backdrop = backdrop,
                        onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
                    )
                    SettingsActionRow(
                        title = "发送测试通知",
                        showDivider = false,
                        onClick = viewModel::sendXuexiaotongTestNotification
                    )
                }
            }

            if (section == PreferencesSection.INTELLIGENCE) {
                IntelligenceSettingsSection(
                    backdrop = backdrop,
                    onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange,
                    viewModel = viewModel
                )
            }

            if (section == PreferencesSection.DATA) SettingsSection(
                title = "数据与隐私",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
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
                canteenUploadConsent?.let { consent ->
                    SettingsToggleRow(
                        title = "参与必吃榜数据共建",
                        subtitle = "上传去标识的食堂消费（POS 机号+时间+金额），不含任何个人信息",
                        selected = consent,
                        onSelectedChange = viewModel::setCanteenUploadConsent,
                        backdrop = backdrop,
                        onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
                    )
                }
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
                    onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange,
                    passiveSwitch = true
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
                SettingsToggleRow(
                    title = "使用内置安全密码键盘",
                    selected = useBuiltInSecurePasswordKeyboard,
                    onSelectedChange = viewModel::setUseBuiltInSecurePasswordKeyboard,
                    backdrop = backdrop,
                    onHorizontalDragActiveChange = onToggleHorizontalDragActiveChange
                )
                SettingsActionRow(
                    title = "清除本地学习记录",
                    destructive = true,
                    showChevron = false,
                    showDivider = false,
                    onClick = { showClearLearningConfirm = true }
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
                    val feedback = rememberAppHapticFeedback()
                    Checkbox(
                        checked = includeHistorical,
                        onCheckedChange = { feedback.perform(AppHapticEvent.Selection); includeHistorical = it }
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
