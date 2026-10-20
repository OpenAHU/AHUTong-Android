package com.ahu.ahutong.ui.screen.attendance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.core.designsystem.R as DesignSystemR
import com.ahu.ahutong.data.attendance.AttendanceRecord
import com.ahu.ahutong.data.attendance.AttendanceStatus
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppTitleIconButton

private val filters = listOf(
    0 to "全部",
    1 to "正常",
    2 to "迟到",
    3 to "缺勤",
    5 to "请假"
)

private const val H_PADDING = 16

@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    AppPageScaffold(
        title = "教务考勤",
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        trailingContent = {
            AppTitleIconButton(
                icon = DesignSystemR.drawable.ic_refresh,
                contentDescription = "刷新",
                loading = state.loading,
                onClick = { viewModel.refresh() }
            )
        },
        bottomPadding = 24.dp,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        lazyContent = {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = H_PADDING.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { (code, label) ->
                    AppFilterChip(
                        selected = state.status == code,
                        onClick = { viewModel.refresh(code) },
                        label = { Text(label) }
                    )
                }
            }
        }

        state.error?.let { message ->
            item {
                AppSectionCard(modifier = Modifier.padding(horizontal = H_PADDING.dp)) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    AppButton(
                        onClick = { viewModel.refresh() },
                        variant = AppButtonVariant.Secondary
                    ) { Text("重试") }
                }
            }
        }

        if (state.loading && state.records.isEmpty() && state.error == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppCircularProgressIndicator()
                }
            }
        }

        if (state.records.isEmpty() && !state.loading && state.error == null) {
            item {
                Text(
                    text = "暂无考勤记录",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = H_PADDING.dp, vertical = 48.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        itemsIndexedRecords(
            records = state.records,
            loading = state.loading
        )

        if (!state.loading && state.records.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.hasMore) {
                        AppButton(
                            onClick = { viewModel.loadMore() },
                            variant = AppButtonVariant.Secondary
                        ) { Text("加载更多") }
                    } else {
                        Text(
                            text = "已加载全部 ${state.totalCount} 条",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        }
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedRecords(
    records: List<AttendanceRecord>,
    loading: Boolean
) {
    records.forEachIndexed { index, record ->
        item {
            AttendanceCard(
                record = record,
                modifier = Modifier.padding(horizontal = H_PADDING.dp)
            )
            if (index == records.lastIndex && loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppCircularProgressIndicator(size = 24.dp, strokeWidth = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun AttendanceCard(record: AttendanceRecord, modifier: Modifier = Modifier) {
    AppSectionCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = record.courseName.ifBlank { "未知课程" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = record.status.label,
                color = statusColor(record.status),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }

        val timeText = buildList {
            if (record.classDate.isNotBlank()) add(record.classDate)
            val span = listOf(record.startTime, record.endTime)
                .filter { it.isNotBlank() }
                .joinToString("-")
            if (span.isNotBlank()) add(span)
        }.joinToString(" ")
        val heading = listOf(record.section, timeText)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (heading.isNotBlank()) {
            Text(
                text = heading,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        val meta = listOf(record.room, record.teacher)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            text = if (record.punchTime.isBlank()) "打卡：未记录" else "打卡：${record.punchTime}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        if (record.canAppeal) {
            Text(
                text = "可申诉",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun statusColor(status: AttendanceStatus): Color = when (status) {
    AttendanceStatus.NORMAL -> Color(0xFF2E7D32)
    AttendanceStatus.LATE -> Color(0xFFEF6C00)
    AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.error
    AttendanceStatus.LEAVE -> Color(0xFF1565C0)
    AttendanceStatus.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
}
