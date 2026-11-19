package com.ahu.ahutong.ui.plugin

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.R
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun capabilityLabel(raw: String): String = when (raw.trim().uppercase()) {
    "NETWORK" -> "网络"
    "PLUGIN_STORAGE" -> "插件存储"
    "CAMERA" -> "相机"
    else -> raw
}

/** 「全部小工具」标题栏的快捷安装入口；仅在插件开关已开启时显示。 */
@Composable
fun PluginQuickInstallButton(
    modifier: Modifier = Modifier,
    onInstalled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var candidate by remember { mutableStateOf<AhupCandidate?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { AhupInstaller.inspect(context, uri) } }
                .onSuccess { candidate = it }
                .onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "无法读取插件包",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    IconButton(
        onClick = { picker.launch(arrayOf("*/*")) },
        modifier = modifier,
        enabled = !busy
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_plugin),
            contentDescription = "安装插件",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }

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
            onDismiss = { if (!busy) candidate = null },
            subtitle = "${c.manifest.title} v${c.manifest.version}",
            actions = listOf(
                AppDialogAction(
                    label = "取消",
                    onClick = { if (!busy) candidate = null },
                    style = AppDialogActionStyle.Neutral,
                    enabled = !busy
                ),
                AppDialogAction(
                    label = if (installBlocked) "请先卸载旧版本" else if (busy) "安装中…" else "安装",
                    enabled = !installBlocked && !busy,
                    onClick = {
                        if (busy || installBlocked) return@AppDialogAction
                        busy = true
                        scope.launch {
                            runCatching {
                                PluginRegistry.update(context) {
                                    AhupInstaller.install(context, c)
                                }
                            }.onSuccess {
                                Toast.makeText(context, "插件已安装", Toast.LENGTH_SHORT).show()
                                onInstalled()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "插件安装失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            busy = false
                            candidate = null
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
