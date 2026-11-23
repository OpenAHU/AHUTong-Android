package com.ahu.ahutong.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ahu.ahutong.core.designsystem.RefreshState
import com.ahu.ahutong.data.status.ServiceHealth
import com.ahu.ahutong.data.status.ServiceStatusIncident
import com.ahu.ahutong.data.status.ServiceStatusMonitor
import com.ahu.ahutong.data.status.ServiceStatusSnapshot
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppRefreshButton
import com.ahu.ahutong.ui.components.AppStateCard
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.ServiceStatusViewModel
import com.ahu.ahutong.ui.state.ServiceStatusViewModel.UiState
import com.kyant.backdrop.Backdrop

private const val STATUS_PAGE_URL = "https://ahustatus.muxyang.com/status/ahutong"

private data class HealthVisual(
    val label: String,
    val summaryTitle: String,
    val color: Color,
    val icon: ImageVector
)

@Composable
fun ServiceStatusScreen(
    onBack: () -> Unit,
    viewModel: ServiceStatusViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val refreshState = (uiState as? UiState.Ready)?.refreshState ?: RefreshState.IDLE

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = "服务状态",
            onBack = onBack,
            backdrop = backdrop,
            bottomPadding = 48.dp,
            actions = {
                AppRefreshButton(
                    state = refreshState,
                    onClick = { viewModel.refresh() }
                )
            }
        ) {
            when (val state = uiState) {
                UiState.Loading -> {
                    AppStateCard.Loading(
                        message = "正在获取服务状态",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp)
                    )
                }

                is UiState.Failed -> {
                    AppStateCard.Error(
                        message = state.message,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 48.dp)
                    )
                }

                is UiState.Ready -> {
                    ServiceStatusContent(
                        snapshot = state.snapshot,
                        updatedAtLabel = state.updatedAtLabel,
                        refreshError = state.refreshError,
                        backdrop = backdrop,
                        onOpenWeb = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(STATUS_PAGE_URL))
                                )
                            }.onFailure {
                                Toast.makeText(context, "无法打开网页", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusContent(
    snapshot: ServiceStatusSnapshot,
    updatedAtLabel: String?,
    refreshError: String?,
    backdrop: Backdrop,
    onOpenWeb: () -> Unit
) {
    val totalCount = snapshot.allMonitors.size
    val upCount = snapshot.upCount
    val overall = healthVisualFor(snapshot.overallHealth)

    OverviewStatusCard(
        visual = overall,
        upCount = upCount,
        totalCount = totalCount,
        updatedAtLabel = updatedAtLabel,
        backdrop = backdrop
    )

    if (refreshError != null) {
        Text(
            text = "刷新失败：$refreshError",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }

    if (snapshot.incidents.isNotEmpty()) {
        SettingsSection(
            title = "服务公告",
            modifier = Modifier.padding(horizontal = 16.dp),
            backdrop = backdrop
        ) {
            snapshot.incidents.forEachIndexed { index, incident ->
                IncidentRow(incident = incident)
                if (index < snapshot.incidents.lastIndex) {
                    RowDivider()
                }
            }
        }
    }

    if (snapshot.groups.isEmpty()) {
        AppStateCard.Empty(
            message = "暂无服务监控项",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        )
    } else {
        snapshot.groups.forEach { group ->
            SettingsSection(
                title = group.name,
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                group.monitors.forEachIndexed { index, monitor ->
                    MonitorRow(monitor = monitor)
                    if (index < group.monitors.lastIndex) {
                        RowDivider()
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "查看完整状态页",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(SmoothRoundedCornerShape(8.dp))
                .clickable(onClick = onOpenWeb)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun OverviewStatusCard(
    visual: HealthVisual,
    upCount: Int,
    totalCount: Int,
    updatedAtLabel: String?,
    backdrop: Backdrop
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        backdrop = backdrop
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(SmoothRoundedCornerShape(12.dp))
                    .background(visual.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    tint = visual.color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = visual.summaryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val subtitle = buildString {
                    append("$upCount/$totalCount 项正常")
                    if (!updatedAtLabel.isNullOrBlank()) {
                        append(" · 更新于 $updatedAtLabel")
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IncidentRow(incident: ServiceStatusIncident) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (incident.title.isNotEmpty()) {
            Text(
                text = incident.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (incident.content.isNotEmpty()) {
            Text(
                text = incident.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonitorRow(monitor: ServiceStatusMonitor) {
    val visual = healthVisualFor(monitor.health)
    val metricParts = buildList {
        monitor.uptime24h?.let { add("24h 可用 ${ServiceStatusViewModel.formatPercent(it)}") }
        monitor.errorRate?.let { add("错误率 ${ServiceStatusViewModel.formatPercent(it)}") }
        if (monitor.errorRate == null) {
            monitor.latestPingMs?.let { add("${it} ms") }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = monitor.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (metricParts.isNotEmpty()) {
                Text(
                    text = metricParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        StatusPill(label = visual.label, color = visual.color)
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun healthVisualFor(health: ServiceHealth): HealthVisual {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (health) {
        ServiceHealth.UP -> HealthVisual(
            label = "正常",
            summaryTitle = "所有服务运行正常",
            color = if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32),
            icon = Icons.Rounded.CheckCircle
        )
        ServiceHealth.PENDING -> HealthVisual(
            label = "波动",
            summaryTitle = "部分服务存在波动",
            color = if (isDark) Color(0xFFFFB74D) else Color(0xFFED6C02),
            icon = Icons.Rounded.Warning
        )
        ServiceHealth.MAINTENANCE -> HealthVisual(
            label = "维护",
            summaryTitle = "部分服务正在维护",
            color = MaterialTheme.colorScheme.primary,
            icon = Icons.Rounded.Schedule
        )
        ServiceHealth.DOWN -> HealthVisual(
            label = "异常",
            summaryTitle = "部分服务当前异常",
            color = MaterialTheme.colorScheme.error,
            icon = Icons.Rounded.Error
        )
    }
}
