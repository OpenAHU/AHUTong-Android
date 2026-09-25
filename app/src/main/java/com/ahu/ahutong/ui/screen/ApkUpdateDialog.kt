package com.ahu.ahutong.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

/**
 * APK 更新弹窗。骨架统一为 AppDialog（P0 弹窗族）；
 * 强制更新或下载中时禁止返回/点外关闭。
 */
@Composable
fun ApkUpdateDialog(
    info: ApkUpdateInfo,
    downloading: Boolean,
    progress: Float? = null,
    downloadElapsedText: String? = null,
    errorText: String? = null,
    apkLocalReady: Boolean = false,
    onConfirm: () -> Unit,
    onInstallLocal: () -> Unit = {},
    onRedownload: () -> Unit = {},
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit = {},
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val progressColor = 70.a1 withNight 80.a1
    val progressTrackColor = 92.n1 withNight 30.n1
    val dismissible = !info.force && !downloading

    val actions = buildList {
        if (apkLocalReady && !downloading) {
            add(AppDialogAction("重新下载", onClick = onRedownload))
        }
        if (downloading && !info.force) {
            add(AppDialogAction("后台下载", onClick = onCancel))
        } else if (!downloading && !info.force) {
            add(AppDialogAction("稍后更新", onClick = onDismiss))
        }
        add(
            AppDialogAction(
                label = when {
                    downloading -> "下载中…"
                    apkLocalReady -> "安装"
                    else -> "下载并安装"
                },
                enabled = !downloading,
                style = AppDialogActionStyle.Primary,
                onClick = if (apkLocalReady && !downloading) onInstallLocal else onConfirm
            )
        )
    }

    AppDialog(
        title = "发现新版本 ${info.versionName ?: info.versionCode}",
        titleStyle = MaterialTheme.typography.headlineSmall,
        titleFontWeight = FontWeight.Bold,
        onDismiss = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible
        ),
        headerContent = {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = contentColor
            )
        },
        contentScrollable = true,
        contentSpacing = 8.dp,
        actions = actions,
        content = {
            Text(
                text = "版本号：${info.versionCode}",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            Text(
                text = "更新内容：",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            Text(
                text = info.changelog?.ifBlank { "暂无更新说明" } ?: "暂无更新说明",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            if (!info.force) {
                Text(
                    text = "跳过后不再自动提醒这个版本，可在设置中手动检查更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryContentColor
                )
                TextButton(onClick = onSkipVersion, modifier = Modifier.fillMaxWidth()) {
                    Text("跳过此版本更新")
                }
            }
            if (!downloadElapsedText.isNullOrBlank()) {
                Text(
                    text = "本次下载耗时：$downloadElapsedText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryContentColor
                )
            }

            if (downloading) {
                Spacer(Modifier.height(4.dp))
                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = progressColor,
                        trackColor = progressTrackColor
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = progressColor,
                        trackColor = progressTrackColor
                    )
                    Text(
                        text = "下载进度：${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }

            if (!errorText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "错误：",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

/** 镜像源切换提示弹窗。 */
@Composable
fun ApkMirrorSourceDialog(
    onUseMirror: () -> Unit,
    onKeepOriginal: () -> Unit,
) {
    AppDialog(
        title = "下载较慢",
        titleStyle = MaterialTheme.typography.headlineSmall,
        titleFontWeight = FontWeight.Bold,
        onDismiss = onKeepOriginal,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        ),
        actions = listOf(
            AppDialogAction("继续原下载", onClick = onKeepOriginal),
            AppDialogAction(
                "使用镜像源",
                onClick = onUseMirror,
                style = AppDialogActionStyle.Primary
            )
        ),
        content = {
            Text(
                text = "当前下载超过 5 秒仍未达到 30%，是否切换到镜像源继续下载？文件校验仍使用官方源提供的信息。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    )
}
