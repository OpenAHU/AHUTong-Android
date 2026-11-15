package com.ahu.ahutong.ui.screen.main.home

import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
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

/** 今日考试随时间变化的部分；首页只关心钟点，不关心学期周次。 */
internal data class HomeExam(
    val name: String,
    val location: String,
    val startMinutes: Int,
    val endMinutes: Int
)

internal data class HomeHeroText(val headline: String, val subtitle: String)

@Composable
internal fun AtAGlance(
    todayCourses: List<Course>,
    currentMinutes: Int,
    currentDateText: String,
    onOpenSchedule: () -> Unit,
    isInSemester: Boolean = true,
    emptyCourseText: String = "已全部上完",
    enabled: Boolean = true,
    todayExams: List<HomeExam> = emptyList(),
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    // 主页排版全主题统一为 Radiant 方案（currentDateText/trailingContent 由曜光头部承担）
    RadiantAtAGlance(
        todayCourses = todayCourses,
        currentMinutes = currentMinutes,
        onOpenSchedule = onOpenSchedule,
        isInSemester = isInSemester,
        todayExams = todayExams,
        enabled = enabled
    )
}


@Composable
private fun RadiantAtAGlance(
    todayCourses: List<Course>,
    currentMinutes: Int,
    onOpenSchedule: () -> Unit,
    isInSemester: Boolean,
    todayExams: List<HomeExam>,
    enabled: Boolean
) {
    val hero = resolveHomeHeroText(todayCourses, todayExams, currentMinutes, isInSemester)
    val headline = hero.headline
    val subtitle = hero.subtitle

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
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** 考试与上课共用同一条时间线：正在进行的优先，其次比下一件事谁先开始。 */
internal fun resolveHomeHeroText(
    todayCourses: List<Course>,
    todayExams: List<HomeExam>,
    currentMinutes: Int,
    isInSemester: Boolean
): HomeHeroText {
    val currentCourse = todayCourses.find {
        currentMinutes in ScheduleSectionTimes.getCourseTimeRangeInMinutes(it)
    }
    val currentCourseIndex = todayCourses.indexOfFirst {
        val range = ScheduleSectionTimes.getCourseTimeRangeInMinutes(it)
        currentMinutes in range || currentMinutes < range.first
    }.takeIf { it != -1 } ?: todayCourses.lastIndex
    val hasRemainingCourses = todayCourses.isNotEmpty() &&
        currentMinutes <= ScheduleSectionTimes.getCourseTimeRangeInMinutes(todayCourses.last()).last
    val nextCourse = todayCourses.getOrNull(currentCourseIndex)?.takeIf { hasRemainingCourses }
    val ongoingExam = todayExams.firstOrNull {
        currentMinutes >= it.startMinutes && currentMinutes < it.endMinutes
    }
    val nextExam = todayExams.firstOrNull { currentMinutes < it.startMinutes }

    return when {
        ongoingExam != null -> HomeHeroText(
            headline = "正在考试 · ${ongoingExam.name}",
            subtitle = "距考试结束还有 ${formatCourseDuration(ongoingExam.endMinutes - currentMinutes)}"
        )

        currentCourse != null -> HomeHeroText(
            headline = "正在上课 · ${currentCourse.name}",
            subtitle = "距下课还有 ${
                formatCourseDuration(
                    ScheduleSectionTimes.getCourseTimeRangeInMinutes(currentCourse).last - currentMinutes
                )
            }"
        )

        nextExam != null && (
            nextCourse == null ||
                nextExam.startMinutes < ScheduleSectionTimes.getCourseTimeRangeInMinutes(nextCourse).first
            ) -> HomeHeroText(
            headline = "下一场考试是 ${nextExam.name}",
            subtitle = "还有 ${formatCourseDuration(nextExam.startMinutes - currentMinutes)}" +
                nextExam.location.takeIf { it.isNotBlank() }?.let { "，在 $it" }.orEmpty()
        )

        nextCourse != null -> HomeHeroText(
            headline = "下节课是 ${nextCourse.name}",
            subtitle = "还有 ${
                formatCourseDuration(
                    ScheduleSectionTimes.getCourseTimeRangeInMinutes(nextCourse).first - currentMinutes
                )
            }，在 ${nextCourse.location}"
        )

        !isInSemester -> HomeHeroText(headline = "假期中", subtitle = "准备您自己的安排吧")
        else -> HomeHeroText(headline = "今日空闲", subtitle = "今天暂无课程安排")
    }
}

private fun formatCourseDuration(durationMinutes: Int): String = when {
    durationMinutes % 60 == 0 -> "${durationMinutes / 60}小时整"
    durationMinutes > 60 -> "${durationMinutes / 60}小时${durationMinutes % 60}分钟"
    else -> "${durationMinutes}分钟"
}
