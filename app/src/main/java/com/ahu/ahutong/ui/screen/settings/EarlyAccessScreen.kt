package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.data.preview.EarlyAccessChoice
import com.ahu.ahutong.data.preview.EarlyAccessManager
import com.ahu.ahutong.data.preview.EarlyAccessState
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsInfoRow
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import kotlinx.coroutines.launch

private const val earlyAccessIntroduction = "加入抢先体验计划后，你可以提前收到新功能版本。抢先版本仍在测试，可能出现功能异常、性能或兼容性问题。\n\n" +
    "你可以随时在 设置 → 应用 → 抢先体验 中退出。退出后只检查正式版本，已安装的抢先版本不会自动降级。"

internal fun earlyAccessSummary(state: EarlyAccessState): String =
    if (state.joined ?: (state.choice == EarlyAccessChoice.Yes)) "已加入" else "未加入"

@Composable
fun EarlyAccessScreen(onBack: () -> Unit) {
    val state by EarlyAccessManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember(state.subject) { mutableStateOf<String?>(null) }
    var confirmLeave by remember(state.subject) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        EarlyAccessManager.refresh().onFailure { error = "暂时无法同步体验状态，请重试。" }
    }
    val displayJoined = state.joined ?: (state.choice == EarlyAccessChoice.Yes)
    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(title = "抢先体验", onBack = onBack, backdrop = backdrop) {
            SettingsSection(title = "当前账号", backdrop = backdrop, modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsInfoRow(
                    title = earlyAccessSummary(state),
                    subtitle = if (displayJoined) "可以收到正式和抢先版本更新" else "当前只接收正式版本更新",
                    showDivider = false
                )
            }
            Text(
                text = earlyAccessIntroduction,
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            error?.let {
                Text(it, modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error)
            }
            SettingsSection(title = "体验计划", backdrop = backdrop, modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsActionRow(
                    title = if (state.isLoading) "正在处理，请稍候" else if (displayJoined) "退出抢先体验" else "加入抢先体验",
                    destructive = displayJoined,
                    onClick = {
                        if (!state.isLoading && state.subject != null) {
                            if (displayJoined) confirmLeave = true else scope.launch {
                                error = null
                                EarlyAccessManager.join().onFailure { error = "加入失败，请检查网络后重试。" }
                            }
                        }
                    }
                )
                SettingsActionRow(
                    title = "刷新状态",
                    showDivider = false,
                    onClick = {
                        if (!state.isLoading) scope.launch {
                            error = null
                            EarlyAccessManager.refresh().onFailure { error = "暂时无法同步体验状态，请重试。" }
                        }
                    }
                )
            }
        }
    }
    if (confirmLeave) {
        AppDialog(
            title = "退出抢先体验？",
            onDismiss = { if (!state.isLoading) confirmLeave = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            actions = listOf(
                AppDialogAction("取消", { confirmLeave = false }, enabled = !state.isLoading),
                AppDialogAction("退出", {
                    scope.launch {
                        error = null
                        EarlyAccessManager.leave().onSuccess { confirmLeave = false }
                            .onFailure { error = "退出失败，请检查网络后重试。"; confirmLeave = false }
                    }
                }, AppDialogActionStyle.Danger, enabled = !state.isLoading)
            ),
            content = { Text("退出后只检查正式版本。已经安装的版本会保留，不会自动降级。") }
        )
    }
}

@Composable
fun EarlyAccessInvitationHost(eligible: Boolean, accountRevision: Int) {
    val state by EarlyAccessManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val account = SessionStore.currentUser()?.xh
    var error by remember(state.subject) { mutableStateOf<String?>(null) }
    var joining by remember(state.subject) { mutableStateOf(false) }
    LaunchedEffect(account, eligible, accountRevision) {
        EarlyAccessManager.synchronizeIdentity()
        if (eligible) EarlyAccessManager.refresh()
    }
    if (!eligible || !state.shouldPrompt || (state.isLoading && !joining)) return
    AppDialog(
        title = "加入抢先体验计划？",
        onDismiss = { if (!state.isLoading) EarlyAccessManager.defer() },
        contentScrollable = true,
        properties = DialogProperties(dismissOnClickOutside = false),
        actions = listOf(
            AppDialogAction("拒绝", { EarlyAccessManager.decline() }, enabled = !state.isLoading),
            AppDialogAction("再想想", { EarlyAccessManager.defer() }, enabled = !state.isLoading),
            AppDialogAction("同意", {
                scope.launch {
                    error = null
                    joining = true
                    try {
                        EarlyAccessManager.join().onFailure { error = "加入失败，请检查网络后重试。" }
                    } finally { joining = false }
                }
            }, AppDialogActionStyle.Primary, enabled = !state.isLoading)
        ),
        content = {
            Text(earlyAccessIntroduction, style = MaterialTheme.typography.bodyMedium)
            if (joining) Text("正在加入，请稍候…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    )
}
