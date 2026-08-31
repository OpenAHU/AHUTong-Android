package com.ahu.ahutong.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ahu.ahutong.AHUApplication
import com.ahu.ahutong.R
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.notification.CourseReminderScheduler
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.sdk.RustSDK
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsConfirmationDialog
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsInfoRow
import com.ahu.ahutong.ui.components.SettingsHeroCard
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.isRadiantUi
import com.ahu.ahutong.ui.components.SettingsPageHeader
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.AboutViewModel
import com.ahu.ahutong.ui.state.MainViewModel
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.launch

@SuppressLint("ContextCastToActivity")
@Composable
fun Settings(
    navController: NavHostController,
    mainViewModel: MainViewModel = viewModel(),
    aboutViewModel: AboutViewModel = viewModel(),
    behaviorRuntime: BehaviorPredictionRuntime
) {
    val context = LocalContext.current as ComponentActivity
    val scope = rememberCoroutineScope()
    val preferencesManager = remember(context) {
        PreferencesManager(context.applicationContext)
    }
    var isClearDataDialogShown by rememberSaveable { mutableStateOf(false) }
    var isUpdateLogDialogShown by rememberSaveable { mutableStateOf(false) }
    var updateLog by remember { mutableStateOf("") }
    val tip by remember { aboutViewModel.tipState }
    var appCardTapCount by remember { mutableIntStateOf(0) }
    var lastAppCardTap by remember { mutableLongStateOf(0L) }

    LaunchedEffect(tip) {
        tip?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            aboutViewModel.tipState.value = null
        }
        runCatching { AhuTong.API.getApkUpdateInfo().changelog.orEmpty() }
            .onSuccess { updateLog = it.ifBlank { "暂无更新说明" } }
            .onFailure { updateLog = "获取失败" }
    }

    val onAppCardClick = {
        val now = System.currentTimeMillis()
        appCardTapCount = if (now - lastAppCardTap > 1_000L) 1 else appCardTapCount + 1
        lastAppCardTap = now
        if (appCardTapCount >= 8) {
            appCardTapCount = 0
            navController.navigate("debug")
        }
    }

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            SettingsPageHeader(title = stringResource(id = R.string.setting))

        val isLiquid = LocalIsLiquidGlassEnabled.current
        val isRadiant = isRadiantUi
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
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(ContinuousCapsule)
                    .background(MaterialTheme.colorScheme.surface)
                    .scale(1.65f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    color = heroContentColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = aboutViewModel.versionName.orEmpty(),
                    color = heroContentColor.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        AHUCache.getCurrentUser()?.let { user ->
            val schoolTerm = AHUCache.getSchoolTerm()?.split('-')
                ?.takeIf { it.size == 3 }
                ?.let { "${it[0]}-${it[1]} 学年 · 第 ${it[2]} 学期" }
            SettingsSection(
                title = "账户",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                SettingsInfoRow(
                    title = user.name,
                    subtitle = schoolTerm
                )
                SettingsActionRow(
                    title = "重新登录",
                    leadingIcon = if (isRadiant) null else Icons.AutoMirrored.Outlined.Login,
                    leadingPainter = if (isRadiant) painterResource(R.drawable.ic_logout) else null,
                    showDivider = false,
                    onClick = { navController.navigate("login") }
                )
            }
        }

        SettingsSection(
            title = "应用",
            modifier = Modifier.padding(horizontal = 16.dp),
            backdrop = backdrop
        ) {
            SettingsActionRow(
                title = stringResource(id = R.string.preferences),
                subtitle = "通知、外观、主页与智能体验",
                leadingIcon = if (isRadiant) null else Icons.Outlined.Tune,
                leadingPainter = if (isRadiant) painterResource(R.drawable.ic_setting_config) else null,
                onClick = { navController.navigate("preferences") }
            )
            SettingsActionRow(
                title = stringResource(id = R.string.check_update),
                leadingIcon = if (isRadiant) null else Icons.Outlined.Update,
                leadingPainter = if (isRadiant) painterResource(R.drawable.ic_update) else null,
                showDivider = false,
                onClick = {
                    mainViewModel.checkApkUpdateManually(context) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        SettingsSection(
            title = "关于与支持",
            modifier = Modifier.padding(horizontal = 16.dp),
            backdrop = backdrop
        ) {
            SettingsActionRow(
                title = stringResource(id = R.string.license),
                leadingIcon = if (isRadiant) null else Icons.AutoMirrored.Outlined.Article,
                leadingPainter = if (isRadiant) painterResource(R.drawable.ic_announcement) else null,
                onClick = { navController.navigate("settings__license") }
            )
            SettingsActionRow(
                title = stringResource(id = R.string.contributors),
                leadingIcon = if (isRadiant) null else Icons.Outlined.PeopleOutline,
                leadingPainter = if (isRadiant) painterResource(R.drawable.ic_peoples) else null,
                onClick = { navController.navigate("settings__contributors") }
            )
            SettingsActionRow(
                title = stringResource(id = R.string.mine_tv_feedback),
                leadingIcon = if (isRadiant) null else Icons.Outlined.Feedback,
                leadingPainter = if (isRadiant) painterResource(R.drawable.ic_topic) else null,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=1006203134&card_type=group&source=qrcode".toUri()
                            ).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP }
                        )
                    }.onFailure {
                        Toast.makeText(context, "请安装 QQ 或 Tim", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            SettingsActionRow(
                title = stringResource(id = R.string.update_intro),
                leadingIcon = if (isRadiant) null else Icons.AutoMirrored.Outlined.Article,
                leadingPainter = if (isRadiant) painterResource(R.drawable.ic_log) else null,
                onClick = { isUpdateLogDialogShown = true }
            )
            SettingsActionRow(
                title = stringResource(id = R.string.setting_clear),
                subtitle = "清除登录状态、课表和本地数据",
                leadingIcon = if (isRadiant) null else Icons.Outlined.ClearAll,
                leadingPainter = if (isRadiant) painterResource(R.drawable.ic_clear) else null,
                destructive = true,
                showDivider = false,
                onClick = { isClearDataDialogShown = true }
            )
            }
        }
    }

    if (isClearDataDialogShown) {
        SettingsConfirmationDialog(
            title = "清除所有数据？",
            message = "登录状态、课表及本机设置将被永久清除。",
            confirmLabel = "清除",
            destructive = true,
            onConfirm = {
                isClearDataDialogShown = false
                scope.launch {
                    CourseReminderScheduler.cancel(context)
                    preferencesManager.clearAll()
                    behaviorRuntime.logoutAndClear()
                    mainViewModel.logout()
                    AHUCache.clearAll()
                    RustSDK.initSafe("")
                    CookieManager.cookieJar.clear()
                    CookieManager.cookieJar.clearSession()
                    AHUApplication.sessionExpired = true
                    Toast.makeText(context, "已清除所有数据", Toast.LENGTH_SHORT).show()
                    navController.navigate("login") { popUpTo(0) }
                }
            },
            onDismiss = { isClearDataDialogShown = false }
        )
    }

    if (isUpdateLogDialogShown) {
        AlertDialog(
            onDismissRequest = { isUpdateLogDialogShown = false },
            title = { Text(stringResource(id = R.string.update_intro)) },
            text = {
                Text(
                    text = updateLog,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { isUpdateLogDialogShown = false }) {
                    Text("完成")
                }
            }
        )
    }
}
