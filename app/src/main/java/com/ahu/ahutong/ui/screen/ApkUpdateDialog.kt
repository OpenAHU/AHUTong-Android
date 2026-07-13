package com.ahu.ahutong.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.ApkDownloadSegment
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun ApkUpdateDialog(
    info: ApkUpdateInfo,
    downloading: Boolean,
    progress: Float? = null,
    activeRangeCount: Int? = null,
    downloadSegments: List<ApkDownloadSegment> = emptyList(),
    downloadElapsedText: String? = null,
    errorText: String? = null,
    apkLocalReady: Boolean = false,
    onConfirm: () -> Unit,
    onInstallLocal: () -> Unit = {},
    onRedownload: () -> Unit = {},
    onDismiss: () -> Unit,
    onCancel: () -> Unit = {},
) {
    val contentColor = 10.n1 withNight 90.n1
    val secondaryContentColor = 45.n1 withNight 75.n1
    val containerColor = 100.n1 withNight 20.n1
    val progressColor = 70.a1 withNight 80.a1
    val progressTrackColor = 92.n1 withNight 30.n1
    val activeSegmentColor = 80.a1.copy(alpha = 0.45f) withNight 55.a1.copy(alpha = 0.65f)

    AlertDialog(
        onDismissRequest = {
            if (!info.force && !downloading) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !info.force && !downloading,
            dismissOnClickOutside = !info.force && !downloading,
        ),
        icon = {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = "Update Icon",
                tint = contentColor
            )
        },
        title = {
            Text(
                text = "发现新版本 ${info.versionName ?: info.versionCode}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "版本号：${info.versionCode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "更新内容：",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = info.changelog?.ifBlank { "暂无更新说明" } ?: "暂无更新说明",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                if (!downloadElapsedText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "本次下载耗时：$downloadElapsedText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryContentColor
                    )
                }

                if (downloading) {
                    Spacer(Modifier.height(12.dp))

                    if (progress == null) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = progressColor,
                            trackColor = progressTrackColor
                        )
                    } else {
                        if (downloadSegments.isEmpty()) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = progressColor,
                                trackColor = progressTrackColor
                            )
                        } else {
                            SegmentedApkProgressIndicator(
                                segments = downloadSegments,
                                trackColor = progressTrackColor,
                                completedColor = progressColor,
                                activeColor = activeSegmentColor
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "下载进度：${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor
                            )
                            if (activeRangeCount != null) {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "活跃分片：${activeRangeCount}片",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }

                if (!errorText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "错误：",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        },
        shape = SmoothRoundedCornerShape(32.dp),
        containerColor = containerColor,
        confirmButton = {
            FilledTonalButton(
                onClick = if (apkLocalReady && !downloading) onInstallLocal else onConfirm,
                enabled = !downloading,
                modifier = Modifier.height(56.dp),
                shape = SmoothRoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = 90.a1 withNight 85.a1,
                    contentColor = 0.n1,
                    disabledContainerColor = 90.a1 withNight 85.a1,
                    disabledContentColor = 0.n1
                )
            ) {
                Text(
                    text = when {
                        downloading -> "下载中…"
                        apkLocalReady -> "安装"
                        else -> "下载并安装"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (apkLocalReady && !downloading) {
                    TextButton(
                        onClick = onRedownload,
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text("重新下载", color = contentColor)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (downloading && !info.force) {
                    TextButton(
                        onClick = onCancel,
                        enabled = true,
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text("后台下载", color = contentColor)
                    }
                } else if (!downloading && !info.force) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = true,
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text("稍后更新", color = contentColor)
                    }
                }
            }
        }
    )
}

@Composable
fun ApkMirrorSourceDialog(
    onUseMirror: () -> Unit,
    onKeepOriginal: () -> Unit,
) {
    val contentColor = 10.n1 withNight 90.n1
    val containerColor = 100.n1 withNight 20.n1

    AlertDialog(
        onDismissRequest = onKeepOriginal,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
        title = {
            Text(
                text = "下载较慢",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor
            )
        },
        text = {
            Text(
                text = "当前下载超过 5 秒仍未达到 30%，是否切换到镜像源继续下载？文件校验仍使用官方源提供的信息。",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        },
        shape = SmoothRoundedCornerShape(32.dp),
        containerColor = containerColor,
        confirmButton = {
            FilledTonalButton(
                onClick = onUseMirror,
                modifier = Modifier.height(56.dp),
                shape = SmoothRoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = 90.a1 withNight 85.a1,
                    contentColor = 0.n1
                )
            ) {
                Text(
                    text = "使用镜像源",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onKeepOriginal,
                modifier = Modifier.height(56.dp),
            ) {
                Text("继续原下载", color = contentColor)
            }
        }
    )
}

@Composable
private fun SegmentedApkProgressIndicator(
    segments: List<ApkDownloadSegment>,
    trackColor: Color,
    completedColor: Color,
    activeColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        val radius = size.height / 2f
        val corner = CornerRadius(radius, radius)
        drawRoundRect(
            color = trackColor,
            cornerRadius = corner,
            size = size
        )

        fun drawSegment(startFraction: Float, endFraction: Float, color: Color) {
            val startX = (startFraction.coerceIn(0f, 1f) * size.width).coerceIn(0f, size.width)
            val endX = (endFraction.coerceIn(0f, 1f) * size.width).coerceIn(0f, size.width)
            val width = endX - startX
            if (width <= 0f) return
            drawRoundRect(
                color = color,
                topLeft = Offset(startX, 0f),
                size = Size(width, size.height),
                cornerRadius = corner
            )
        }

        segments.filter { it.running }.forEach { segment ->
            drawSegment(segment.startFraction, segment.endFraction, activeColor)
        }
        segments.forEach { segment ->
            drawSegment(segment.startFraction, segment.downloadedEndFraction, completedColor)
        }
    }
}
