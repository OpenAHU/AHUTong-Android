package com.ahu.ahutong.ui.screen.main.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes
import com.ahu.ahutong.data.schedule.isCustomCourse
import com.ahu.ahutong.ui.components.AppDialogSurface

@Composable
fun CourseDetailDialog(
    course: Course,
    onDismiss: () -> Unit,
    onEditCustom: (() -> Unit)? = null
) {
    val isCustom = course.isCustomCourse()
    val firstSection = course.startTime
    val lastSection = firstSection + course.length - 1
    val sectionText = if (firstSection == lastSection) {
        "第${firstSection}节"
    } else {
        "第${firstSection}-${lastSection}节"
    }
    AppDialogSurface(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.headlineMedium
                )
                if (isCustom) {
                    Text(
                        text = "自定义课程 · 仅保存在本机",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${course.weekRangeText()} · ${scheduleWeekdayLabel(course.weekday)} · $sectionText",
                    style = MaterialTheme.typography.titleMedium
                )
                val hasCustomTime = isCustom && !course.clockRange.isNullOrBlank()
                ScheduleSectionTimes.getCourseClockRange(course)?.let { clockRange ->
                    Text(
                        text = if (hasCustomTime) "自定义时间 $clockRange" else clockRange,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (hasCustomTime) {
                    Text(
                        text = "课表网格按最近节次吸附显示",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                course.extra?.takeIf(String::isNotBlank)?.let { note ->
                    Text(
                        text = "备注：$note",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(24.dp, 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = course.location?.takeIf(String::isNotBlank) ?: "地点未填写",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(24.dp, 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = course.teacher?.takeIf(String::isNotBlank) ?: "教师未填写",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            if (isCustom && onEditCustom != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                TextButton(
                    onClick = onEditCustom,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("编辑自定义课程")
                }
            }
        }
    }
}
