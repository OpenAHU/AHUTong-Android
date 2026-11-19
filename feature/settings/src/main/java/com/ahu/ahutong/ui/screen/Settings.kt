package com.ahu.ahutong.ui.screen

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SwitchAccount
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.rounded.Search
import com.ahu.ahutong.ui.haptics.HapticAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ahu.ahutong.ui.haptics.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.ui.state.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ahu.ahutong.feature.settings.R
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsConfirmationDialog
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsInfoRow
import com.ahu.ahutong.ui.components.SettingsHeroCard
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.LocalAppUiTheme
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.AppHeaderIconButton
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.components.tourTarget
import com.ahu.ahutong.ui.components.TourBounds
import com.ahu.ahutong.ui.components.TourScrollRequest
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Delete
import top.yukonga.miuix.kmp.icon.icons.useful.Edit
import top.yukonga.miuix.kmp.icon.icons.useful.Info
import top.yukonga.miuix.kmp.icon.icons.useful.Personal
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import top.yukonga.miuix.kmp.icon.icons.useful.Update

@SuppressLint("ContextCastToActivity")
@Composable
fun Settings(
    onNavigateToLogin: () -> Unit,
    onSwitchAccount: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenCampusCard: () -> Unit,
    onOpenNotifications: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenIntelligence: () -> Unit,
    onOpenDataPrivacy: () -> Unit,
    onNavigateToEarlyAccess: () -> Unit,
    earlyAccessSummary: String,
    onNavigateToDebug: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onOpenFeedback: () -> Unit,
    onCheckUpdate: ((String) -> Unit) -> Unit,
    accountName: String?,
    scheduleSummary: String,
    debugBuild: Boolean,
    appName: String,
    appIcon: Painter,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current as ComponentActivity
    val tip = settingsViewModel.tip
    var appCardTapCount by remember { mutableIntStateOf(0) }
    var lastAppCardTap by remember { mutableLongStateOf(0L) }
    val useMiuixIcons = LocalAppUiTheme.current == AppUiTheme.MIUIX
    val isRadiant = isRadiantUi
    var showSettingsSearch by remember { mutableStateOf(false) }

    val searchEntries = buildList {
        add(
            SettingsSearchEntry(
                title = "AI 设置",
                keywords = listOf("AI", "智能", "模型", "大模型", "试用", "密钥", "API", "对话", "助手"),
                open = onOpenAiSettings
            )
        )
        add(
            SettingsSearchEntry(
                title = "主页设置",
                keywords = listOf("主页", "校园卡", "付款码", "二维码", "上屏", "优先级", "排序", "主标题", "hero", "卡片"),
                open = onOpenCampusCard
            )
        )
        add(
            SettingsSearchEntry(
                title = "智能体验",
                keywords = listOf("个性化", "推荐", "预测", "学习", "行为", "猜你想用"),
                open = onOpenIntelligence
            )
        )
        add(
            SettingsSearchEntry(
                title = "插件装载",
                keywords = listOf("插件", "安装", "ahup", "扩展", "卸载"),
                open = onNavigateToPlugins
            )
        )
        add(
            SettingsSearchEntry(
                title = "外观与主题",
                keywords = listOf("主题", "深色", "夜间", "浅色", "颜色", "字体", "字号", "玻璃", "圆角", "外观"),
                open = onOpenAppearance
            )
        )
        add(
            SettingsSearchEntry(
                title = "数据与隐私",
                keywords = listOf("隐私", "权限", "安全键盘", "密码", "数据", "清除", "缓存", "训练", "必吃榜", "上传", "学习记录"),
                open = onOpenDataPrivacy
            )
        )
        add(
            SettingsSearchEntry(
                title = "通知提醒",
                keywords = listOf("通知", "提醒", "推送", "课前", "作业提醒", "电费", "预警"),
                open = onOpenNotifications
            )
        )
        add(
            SettingsSearchEntry(
                title = stringResource(id = R.string.check_update),
                keywords = listOf("更新", "升级", "版本", "新版本"),
                open = {
                    onCheckUpdate { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        )
        add(
            SettingsSearchEntry(
                title = "抢先体验",
                keywords = listOf("抢先", "内测", "beta", "体验"),
                open = onNavigateToEarlyAccess
            )
        )
        add(
            SettingsSearchEntry(
                title = "问题反馈",
                keywords = listOf("反馈", "意见", "建议", "问题", "bug", "报错"),
                open = onOpenFeedback
            )
        )
        add(
            SettingsSearchEntry(
                title = "关于安大通",
                keywords = listOf("关于", "版本", "官网", "贡献者", "贡献名单", "开源", "许可", "隐私政策"),
                open = onNavigateToAbout
            )
        )
        if (accountName != null) {
            add(
                SettingsSearchEntry(
                    title = "个人信息",
                    keywords = listOf("个人信息", "资料", "学籍", "账号"),
                    open = onNavigateToProfile
                )
            )
            add(
                SettingsSearchEntry(
                    title = "重新登录",
                    keywords = listOf("登录", "退出", "重登", "密码"),
                    open = onNavigateToLogin
                )
            )
            add(
                SettingsSearchEntry(
                    title = "切换账号",
                    keywords = listOf("切换", "换号", "多账号"),
                    open = onSwitchAccount
                )
            )
        }
        if (debugBuild) {
            add(
                SettingsSearchEntry(
                    title = "开发者选项",
                    keywords = listOf("开发者", "debug", "调试"),
                    open = onNavigateToDebug
                )
            )
        }
    }

    LaunchedEffect(tip) {
        tip?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            settingsViewModel.tip = null
        }
    }

    val onAppCardClick: () -> Unit = if (debugBuild) {
        {
            val now = System.currentTimeMillis()
            appCardTapCount = if (now - lastAppCardTap > 1_000L) 1 else appCardTapCount + 1
            lastAppCardTap = now
            if (appCardTapCount >= 8) {
                appCardTapCount = 0
                onNavigateToDebug()
            }
        }
    } else {
        {}
    }

    val pageScrollState = rememberScrollState()
    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    LaunchedEffect(Unit) {
        snapshotFlow { TourScrollRequest.signal to TourScrollRequest.targetId }
            .collectLatest { (_, targetId) ->
                if (targetId == null || !targetId.startsWith("settings_")) return@collectLatest
                // 目标可能还没完成首次布局：等到坐标出现为止
                var rect: androidx.compose.ui.geometry.Rect? = null
                var attempts = 0
                while (rect == null && attempts < 20) {
                    delay(50)
                    rect = TourBounds.get(targetId)
                    attempts += 1
                }
                var target = rect ?: return@collectLatest
                repeat(2) { attempt ->
                    val delta = (target.top + target.bottom) / 2f - screenHeightPx / 2f
                    if (kotlin.math.abs(delta) <= 8f) return@collectLatest
                    pageScrollState.animateScrollBy(
                        delta,
                        tween(durationMillis = 320, easing = FastOutSlowInEasing)
                    )
                    // 滚动后重新取一次，处理被边界夹住的情况
                    target = TourBounds.get(targetId) ?: return@collectLatest
                }
            }
    }

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        if (showSettingsSearch) {
            androidx.activity.compose.BackHandler { showSettingsSearch = false }
            SettingsSearchPage(
                entries = searchEntries,
                backdrop = backdrop,
                onBack = { showSettingsSearch = false }
            )
            return@SettingsBackdropContainer
        }
        SettingsPageLayout(
            title = stringResource(id = R.string.setting),
            scrollState = pageScrollState,
            backdrop = backdrop,
            actions = {
                AppHeaderIconButton(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "搜索设置",
                    onClick = { showSettingsSearch = true },
                    backdrop = backdrop
                )
            }
        ) {
        val isLiquid = LocalIsLiquidGlassEnabled.current
        val heroContentColor = if (isLiquid) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
        SettingsHeroCard(
            backdrop = backdrop,
            onClick = onAppCardClick,
            modifier = Modifier
                .padding(horizontal = 16.dp)
        ) {
            Image(
                painter = appIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(ContinuousCapsule)
                    .background(Color.White)
                    .scale(1.65f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = appName,
                    color = heroContentColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = settingsViewModel.versionName.orEmpty(),
                    color = heroContentColor.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        accountName?.let { name ->
            SettingsSection(
                title = "账户",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                SettingsInfoRow(
                    title = name,
                    subtitle = scheduleSummary,
                    onClick = onNavigateToProfile
                )
                SettingsActionRow(
                    title = "重新登录",
                    leadingPainter = painterResource(R.drawable.ic_logout),
                    onClick = onNavigateToLogin
                )
                SettingsActionRow(
                    title = "切换账号",
                    leadingIcon = when {
                        useMiuixIcons -> MiuixIcons.Useful.Refresh
                        isRadiant -> null
                        else -> Icons.Outlined.SwitchAccount
                    },
                    leadingPainter = if (isRadiant) painterResource(R.drawable.ic_switch_account) else null,
                    showDivider = false,
                    onClick = onSwitchAccount
                )
            }
        }

        SettingsSection(
            title = "服务",
            modifier = Modifier.padding(horizontal = 16.dp),
            backdrop = backdrop
        ) {
            SettingsActionRow(
                title = "AI 设置",
                leadingPainter = painterResource(R.drawable.ic_assistant_sparkle),
                onClick = onOpenAiSettings
            )
            SettingsActionRow(
                title = "主页设置",
                leadingPainter = painterResource(R.drawable.ic_home),
                onClick = onOpenCampusCard
            )
            SettingsActionRow(
                title = "智能体验",
                leadingPainter = painterResource(R.drawable.ic_brain),
                onClick = onOpenIntelligence
            )
            SettingsActionRow(
                title = "插件装载",
                leadingPainter = painterResource(R.drawable.ic_plugin),
                showDivider = false,
                onClick = onNavigateToPlugins
            )
        }

        SettingsSection(
            title = "通用",
            modifier = Modifier.padding(horizontal = 16.dp),
            backdrop = backdrop
        ) {
            SettingsActionRow(
                title = "外观与主题",
                modifier = Modifier
                    .tourTarget("settings_appearance"),
                leadingPainter = painterResource(R.drawable.ic_palette),
                onClick = onOpenAppearance
            )
            SettingsActionRow(
                title = "数据与隐私",
                leadingPainter = painterResource(R.drawable.ic_shield),
                onClick = onOpenDataPrivacy
            )
            SettingsActionRow(
                title = "通知提醒",
                modifier = Modifier
                    .tourTarget("settings_notifications"),
                leadingPainter = painterResource(R.drawable.ic_remind),
                showDivider = false,
                onClick = onOpenNotifications
            )
        }

        SettingsSection(
            title = "关于与支持",
            modifier = Modifier.padding(horizontal = 16.dp),
            backdrop = backdrop
        ) {
            SettingsActionRow(
                title = stringResource(id = R.string.check_update),
                leadingPainter = painterResource(R.drawable.ic_refresh),
                onClick = {
                    onCheckUpdate { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
            SettingsActionRow(
                title = "抢先体验",
                value = earlyAccessSummary,
                leadingPainter = painterResource(R.drawable.ic_experiment),
                onClick = onNavigateToEarlyAccess
            )
            SettingsActionRow(
                title = "问题反馈",
                modifier = Modifier
                    .tourTarget("settings_feedback"),
                leadingPainter = painterResource(R.drawable.ic_feedback),
                onClick = onOpenFeedback
            )
            SettingsActionRow(
                title = "关于安大通",
                leadingPainter = painterResource(R.drawable.ic_info),
                showDivider = false,
                onClick = onNavigateToAbout
            )
        }
    }
}
}
