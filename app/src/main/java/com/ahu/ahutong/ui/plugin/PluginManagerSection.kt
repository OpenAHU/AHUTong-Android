package com.ahu.ahutong.ui.plugin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.components.SettingsToggleRow
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** manifest 能力字符串 → 中文标签（与 PluginCapability 枚举名一一对应）。 */
private fun capabilityLabel(raw: String): String = when (raw.trim().uppercase()) {
    "NETWORK" -> "网络"
    "PLUGIN_STORAGE" -> "插件存储"
    "CAMERA" -> "相机"
    else -> raw
}

/**
 * 小工具页尾部的插件管理区。
 *
 * 门禁链：开发者开关（关 = 全部卸载 + 存储清场）→ 开启时强制阅读免责（10 秒倒计时，只此一次）
 * → 之后安装只弹插件自己的说明 + 顶部一句红色风险提醒。
 */
@Composable
fun PluginManagerSection(backdrop: Backdrop) {
    val context = LocalContext.current
    var devMode by remember { mutableStateOf(AhupGate.isDevMode(context)) }
    var showEnableDisclaimer by remember { mutableStateOf(false) }
    var showDisableConfirm by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<AhupCandidate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var listTick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runPluginOperation(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                error = null
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "插件操作失败"
            } finally {
                busy = false
            }
        }
    }

    val runtimePlugins by produceState<List<Pair<String, AhupManifest?>>>(emptyList(), devMode, listTick) {
        value = withContext(Dispatchers.IO) {
            if (devMode) AhupInstaller.installedIds(context).map { it to AhupInstaller.manifestOf(context, it) }
            else emptyList()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runPluginOperation {
                candidate = withContext(Dispatchers.IO) { AhupInstaller.inspect(context, uri) }
            }
        }
    }

    SettingsSection(
        title = "插件",
        backdrop = backdrop
    ) {
        // 开关本体只展示状态，点按先走确认流程；devMode 只在确认后改变
        SettingsToggleRow(
            title = "允许安装插件",
            subtitle = if (devMode) "已开启" else null,
            selected = devMode,
            onSelectedChange = { wantOn ->
                if (wantOn) showEnableDisclaimer = true else showDisableConfirm = true
            },
            backdrop = backdrop
        )
        if (devMode) {
            runtimePlugins.forEach { (id, manifest) ->
                SettingsActionRow(
                    title = manifest?.title ?: id,
                    subtitle = "v${manifest?.version ?: "?"} · ${manifest?.author ?: "未知"}",
                    value = "卸载",
                    showChevron = false,
                    onClick = {
                        runPluginOperation {
                            PluginRegistry.update(context) {
                                AhupInstaller.uninstall(context, id)
                                cleanupPluginData(context, id)
                            }
                            listTick++
                        }
                    }
                )
            }
            RuntimePluginLoader.lastErrors.forEach { err ->
                SettingsActionRow(
                    title = "装载失败",
                    subtitle = err,
                    destructive = true,
                    showChevron = false,
                    onClick = {}
                )
            }
            if (runtimePlugins.isEmpty()) {
                SettingsActionRow(
                    title = "尚未安装插件",
                    showChevron = false,
                    onClick = {}
                )
            }
            SettingsActionRow(
                title = "安装插件包",
                value = ".ahup",
                showChevron = false,
                showDivider = error == null,
                onClick = { picker.launch(arrayOf("*/*")) }
            )
            error?.let {
                SettingsActionRow(
                    title = "操作失败",
                    subtitle = it,
                    destructive = true,
                    showChevron = false,
                    showDivider = false,
                    onClick = {}
                )
            }
        }
    }

    // 开启开发者模式：强制阅读免责（10 秒倒计时，只此一次）
    if (showEnableDisclaimer) {
        var remaining by remember { mutableIntStateOf(10) }
        LaunchedEffect(Unit) {
            while (remaining > 0) {
                delay(1000)
                remaining--
            }
        }
        AppDialog(
            title = "插件功能须知",
            onDismiss = { showEnableDisclaimer = false },
            actions = listOf(
                AppDialogAction(
                    label = "取消",
                    onClick = { showEnableDisclaimer = false },
                    style = AppDialogActionStyle.Neutral
                ),
                AppDialogAction(
                    label = if (remaining > 0) "我已阅读（${remaining}s）" else "我已阅读，开启",
                    onClick = {
                        if (remaining > 0) return@AppDialogAction
                        runPluginOperation {
                            AhupGate.setDevMode(context, true)
                            devMode = true
                            showEnableDisclaimer = false
                        }
                    },
                    style = AppDialogActionStyle.Primary
                )
            )
        ) {
            Text(
                "插件与安大通主程序同进程运行，可信插件可能触及 App 数据。",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "· 只安装你信任来源的 .ahup 包\n" +
                    "· 优先安装带「官方开发」签名的插件\n" +
                    "· 未签名/签名无效的插件风险自负\n" +
                    "· 插件内容由插件作者负责，与安大通无关\n" +
                    "· 关闭此开关将卸载全部插件并清空其数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 关闭确认：明确后果
    if (showDisableConfirm) {
        AppDialog(
            title = "关闭插件功能？",
            onDismiss = { showDisableConfirm = false },
            actions = listOf(
                AppDialogAction(
                    label = "取消",
                    onClick = { showDisableConfirm = false },
                    style = AppDialogActionStyle.Neutral
                ),
                AppDialogAction(
                    label = "关闭并卸载全部",
                    onClick = {
                        runPluginOperation {
                            AhupGate.setDevMode(context, false)
                            devMode = false
                            showDisableConfirm = false
                        }
                    },
                    style = AppDialogActionStyle.Danger
                )
            )
        ) {
            Text(
                "关闭后将卸载所有已安装插件，并清空它们的存储数据。此操作不可撤销。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // 安装确认（开发者模式已开，只弹插件自己的说明 + 顶部红色提醒）
    candidate?.let { c ->
        val signatureText = when (c.signatureStatus) {
            AhupInstaller.SignatureStatus.TRUSTED -> "✅ 该插件由官方开发"
            AhupInstaller.SignatureStatus.KNOWN ->
                "✅ 同一作者（已信任）· 指纹 ${c.manifest.signerFingerprint.take(16)}"
            AhupInstaller.SignatureStatus.UNKNOWN ->
                "⚠️ 新作者 · 指纹 ${c.manifest.signerFingerprint.take(16)}"
            AhupInstaller.SignatureStatus.UNSIGNED -> "⚠️ 未签名——仅安装你信任来源的插件包"
            AhupInstaller.SignatureStatus.INVALID -> "🚫 签名无效——包可能被篡改"
        }
        val installBlocked = c.signerChanged
        AppDialog(
            title = "安装插件",
            onDismiss = { candidate = null },
            subtitle = "${c.manifest.title} v${c.manifest.version}",
            actions = listOf(
                AppDialogAction(
                    label = "取消",
                    onClick = { candidate = null },
                    style = AppDialogActionStyle.Neutral
                ),
                AppDialogAction(
                    label = if (installBlocked) "请先卸载旧版本" else "安装",
                    enabled = !installBlocked,
                    onClick = {
                        runPluginOperation {
                            PluginRegistry.update(context) { AhupInstaller.install(context, c) }
                            candidate = null
                            listTick++
                        }
                    },
                    style = if (c.signatureStatus == AhupInstaller.SignatureStatus.INVALID ||
                        installBlocked
                    ) {
                        AppDialogActionStyle.Danger
                    } else {
                        AppDialogActionStyle.Primary
                    }
                )
            )
        ) {
            Text(
                "再次提醒：插件与主程序同进程运行，请只安装可信来源的插件，内容风险由插件作者承担。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text("作者：${c.manifest.author}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "API ${c.manifest.apiLevel} · SDK ${c.manifest.sdkVersion.ifBlank { "未标注" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (installBlocked) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "该插件已安装，但签名指纹与当前包不一致。为防冒名，请先卸载旧版本。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "声明能力：${c.manifest.capabilities.ifEmpty { setOf("无") }.joinToString("、") { capabilityLabel(it) }}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                signatureText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
