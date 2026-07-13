package com.ahu.ahutong.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import com.ahu.ahutong.feature.update.R
import com.ahu.ahutong.ui.components.AhuDialog
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.theme.AhuColors
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
    val contentColor = AhuColors.onSurface
    val secondaryContentColor = 45.n1 withNight 75.n1
    val progressColor = 70.a1 withNight 80.a1
    val progressTrackColor = 92.n1 withNight 30.n1
    val activeSegmentColor = 80.a1.copy(alpha = 0.45f) withNight 55.a1.copy(alpha = 0.65f)

    AhuDialog(
        onDismissRequest = {
            if (!info.force && !downloading) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !info.force && !downloading,
            dismissOnClickOutside = !info.force && !downloading,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = "Update Icon",
                tint = contentColor
            )
            Text(
                text = stringResource(
                    id = R.string.new_version_found,
                    info.versionName ?: info.versionCode.toString()
                ),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(id = R.string.version_code, info.versionCode),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.update_content),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = info.changelog?.ifBlank { null }
                    ?: stringResource(id = R.string.no_update_log),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            if (!downloadElapsedText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.download_elapsed, downloadElapsedText),
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
                            text = stringResource(
                                id = R.string.download_progress,
                                (progress * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor
                        )
                        if (activeRangeCount != null) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(
                                    id = R.string.active_segments,
                                    activeRangeCount
                                ),
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
                    text = stringResource(id = R.string.error_label),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (apkLocalReady && !downloading) {
                TextButton(onClick = onRedownload) {
                    Text(stringResource(id = R.string.redownload), color = contentColor)
                }
            }
            if (downloading && !info.force) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(id = R.string.background_download), color = contentColor)
                }
            } else if (!downloading && !info.force) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.update_later), color = contentColor)
                }
            }
            AhuPrimaryButton(
                text = when {
                    downloading -> stringResource(id = R.string.downloading)
                    apkLocalReady -> stringResource(id = R.string.install)
                    else -> stringResource(id = R.string.download_and_install)
                },
                onClick = if (apkLocalReady && !downloading) onInstallLocal else onConfirm,
                enabled = !downloading,
            )
        }
    }
}

@Composable
fun ApkMirrorSourceDialog(
    onUseMirror: () -> Unit,
    onKeepOriginal: () -> Unit,
) {
    val contentColor = AhuColors.onSurface

    AhuDialog(
        onDismissRequest = onKeepOriginal,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Text(
            text = stringResource(id = R.string.slow_download_title),
            modifier = Modifier.padding(horizontal = 24.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            color = contentColor
        )
        Text(
            text = stringResource(id = R.string.slow_download_message),
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onKeepOriginal) {
                Text(stringResource(id = R.string.continue_original_download), color = contentColor)
            }
            AhuPrimaryButton(
                text = stringResource(id = R.string.use_mirror_source),
                onClick = onUseMirror,
            )
        }
    }
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
