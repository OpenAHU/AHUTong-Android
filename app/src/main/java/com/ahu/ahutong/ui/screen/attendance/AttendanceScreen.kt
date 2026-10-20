package com.ahu.ahutong.ui.screen.attendance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
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
import com.ahu.ahutong.data.attendance.AttendanceRecord
import com.ahu.ahutong.data.attendance.AttendanceStatus
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppHeaderIconButton
import com.ahu.ahutong.ui.components.AppPageHeader
import com.ahu.ahutong.ui.components.AppSectionCard

private val filters = listOf(
    0 to "全部",
    1 to "正常",
    2 to "迟到",
    3 to "缺勤",
    5 to "请假"
)

@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        AppPageHeader(
            title = "教务考勤",
            onBack = onBack,
            actions = {
                AppHeaderIconButton(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "刷新",
                    onClick = { viewModel.refresh() }
                )
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
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

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.error?.let { message ->
                item {
                    AppSectionCard {
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

            if (state.records.isEmpty() && !state.loading && state.error == null) {
                item {
                    Text(
                        text = "暂无考勤记录",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            itemsIndexed(state.records) { index, record ->
                AttendanceCard(record = record)
                if (index == state.records.lastIndex && state.loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppCircularProgressIndicator(size = 24.dp, strokeWidth = 3.dp)
                    }
                }
            }

            if (!state.loading && state.records.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
    }
}

@Composable
private fun AttendanceCard(record: AttendanceRecord) {
    AppSectionCard {
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
