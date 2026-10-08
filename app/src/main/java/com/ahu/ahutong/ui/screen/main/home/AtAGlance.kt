package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.monet.a1
import com.kyant.monet.withNight

@Composable
fun AtAGlance(
    todayCourses: List<Course>,
    currentMinutes: Int,
    currentDateText: String,
    onOpenSchedule: () -> Unit,
    isInSemester: Boolean = true,
    emptyCourseText: String = "已全部上完",
    enabled: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    // 主页排版全主题统一为 Radiant 方案（currentDateText/trailingContent 由曜光头部承担）
    RadiantAtAGlance(
        todayCourses = todayCourses,
        currentMinutes = currentMinutes,
        onOpenSchedule = onOpenSchedule,
        isInSemester = isInSemester,
        enabled = enabled
    )
}


@Composable
private fun RadiantAtAGlance(
    todayCourses: List<Course>,
    currentMinutes: Int,
    onOpenSchedule: () -> Unit,
    isInSemester: Boolean,
    enabled: Boolean
) {
    val currentCourse = todayCourses.find {
        currentMinutes in ScheduleSectionTimes.getCourseTimeRangeInMinutes(it)
    }
    val currentCourseIndex = todayCourses.indexOfFirst {
        val range = ScheduleSectionTimes.getCourseTimeRangeInMinutes(it)
        currentMinutes in range || currentMinutes < range.first
    }.takeIf { it != -1 } ?: todayCourses.lastIndex
    val hasRemainingCourses = todayCourses.isNotEmpty() &&
        currentMinutes <= ScheduleSectionTimes.getCourseTimeRangeInMinutes(todayCourses.last()).last
    val headline = when {
        currentCourse != null -> "正在上课 · ${currentCourse.name}"
        hasRemainingCourses -> "下节课是 ${todayCourses[currentCourseIndex].name}"
        !isInSemester -> "假期中"
        else -> "今日空闲"
    }
    val subtitle = when {
        currentCourse != null -> {
            val duration = ScheduleSectionTimes.getCourseTimeRangeInMinutes(currentCourse).last -
                currentMinutes
            "距下课还有 ${formatCourseDuration(duration)}"
        }

        hasRemainingCourses -> {
            val nextCourse = todayCourses[currentCourseIndex]
            val duration = ScheduleSectionTimes.getCourseTimeRangeInMinutes(nextCourse).first -
                currentMinutes
            "还有 ${formatCourseDuration(duration)}，在 ${nextCourse.location}"
        }

        !isInSemester -> "准备您自己的安排吧"
        else -> "今天暂无课程安排"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onOpenSchedule) else Modifier)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = headline,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = subtitle,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatCourseDuration(durationMinutes: Int): String = when {
    durationMinutes % 60 == 0 -> "${durationMinutes / 60}小时整"
    durationMinutes > 60 -> "${durationMinutes / 60}小时${durationMinutes % 60}分钟"
    else -> "${durationMinutes}分钟"
}
