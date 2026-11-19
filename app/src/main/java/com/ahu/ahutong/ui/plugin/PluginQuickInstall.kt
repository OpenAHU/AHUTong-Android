package com.ahu.ahutong.ui.plugin

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun capabilityLabel(raw: String): String = when (raw.trim().uppercase()) {
    "NETWORK" -> "网络"
    "PLUGIN_STORAGE" -> "插件存储"
    "CAMERA" -> "相机"
    else -> raw
}

/**
 * 「全部小工具」标题栏快捷安装入口的状态。
 * 按钮本身交给标题栏的 actions 渲染，这里只管选包、确认与安装。
 */
@Stable
class PluginQuickInstallState internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onInstalled: () -> Unit
) {
    var candidate by mutableStateOf<AhupCandidate?>(null)
        internal set
    var busy by mutableStateOf(false)
        internal set

    internal var launchPicker: () -> Unit = {}

    fun pick() {
        if (!busy) launchPicker()
    }

    internal fun onPicked(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { AhupInstaller.inspect(context, uri) } }
                .onSuccess { candidate = it }
                .onFailure {
                    Toast.makeText(context, it.message ?: "无法读取插件包", Toast.LENGTH_SHORT).show()
                }
        }
    }

    internal fun dismiss() {
        if (!busy) candidate = null
    }

    internal fun install() {
        val c = candidate ?: return
        if (busy || c.signerChanged) return
        busy = true
        scope.launch {
            runCatching {
                PluginRegistry.update(context) { AhupInstaller.install(context, c) }
            }.onSuccess {
                Toast.makeText(context, "插件已安装", Toast.LENGTH_SHORT).show()
                onInstalled()
            }.onFailure {
                Toast.makeText(context, it.message ?: "插件安装失败", Toast.LENGTH_SHORT).show()
            }
            busy = false
            candidate = null
        }
    }
}

@Composable
fun rememberPluginQuickInstallState(onInstalled: () -> Unit = {}): PluginQuickInstallState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnInstalled = rememberUpdatedState(onInstalled)
    val state = remember(context, scope) {
        PluginQuickInstallState(context, scope) { currentOnInstalled.value() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        state.onPicked(uri)
    }
    SideEffect { state.launchPicker = { picker.launch(arrayOf("*/*")) } }
    return state
}

@Composable
fun PluginQuickInstallDialogs(state: PluginQuickInstallState) {
    state.candidate?.let { c ->
        val busy = state.busy
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
            onDismiss = state::dismiss,
            subtitle = "${c.manifest.title} v${c.manifest.version}",
            actions = listOf(
                AppDialogAction(
                    label = "取消",
                    onClick = state::dismiss,
                    style = AppDialogActionStyle.Neutral,
                    enabled = !busy
                ),
                AppDialogAction(
                    label = if (installBlocked) "请先卸载旧版本" else if (busy) "安装中…" else "安装",
                    enabled = !installBlocked && !busy,
                    onClick = state::install,
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
                "插件与主程序同进程运行，请只安装可信来源的插件，内容风险由插件作者承担。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text("作者：${c.manifest.author}", style = MaterialTheme.typography.bodyMedium)
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
