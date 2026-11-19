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
import com.ahu.ahutong.ui.haptics.HapticAlertDialog as AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ahu.ahutong.ui.haptics.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
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
import com.ahu.ahutong.ui.components.SettingsSection
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

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = stringResource(id = R.string.setting),
            backdrop = backdrop
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
                title = "插件与安装",
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
                leadingPainter = painterResource(R.drawable.ic_palette),
                onClick = onOpenAppearance
            )
            SettingsActionRow(
                title = "数据与隐私",
                leadingPainter = painterResource(R.drawable.ic_shield),
                onClick = onOpenDataPrivacy
            )
            SettingsActionRow(
                title = "通知与提醒",
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
                title = "反馈",
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
