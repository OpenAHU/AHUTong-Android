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

/** 待交作业：首页只关心标题、课程名与截止时刻，数据全部来自本地缓存。 */
internal data class HomeAssignment(
    val title: String,
    val courseName: String,
    val endTs: Long
)

internal enum class HomeHeroTarget { SCHEDULE, XUEXIAOTONG_SCHEDULE }

internal data class HomeHeroText(
    val headline: String,
    val subtitle: String,
    val target: HomeHeroTarget = HomeHeroTarget.SCHEDULE
)

@Composable
internal fun AtAGlance(
    todayCourses: List<Course>,
    currentMinutes: Int,
    currentDateText: String,
    onOpenSchedule: () -> Unit,
    onOpenXuexiaotongSchedule: () -> Unit = onOpenSchedule,
    isInSemester: Boolean = true,
    emptyCourseText: String = "已全部上完",
    enabled: Boolean = true,
    todayExams: List<HomeExam> = emptyList(),
    assignments: List<HomeAssignment> = emptyList(),
    nowMillis: Long = 0L,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    // 主页排版全主题统一为 Radiant 方案（currentDateText/trailingContent 由曜光头部承担）
    RadiantAtAGlance(
        todayCourses = todayCourses,
        currentMinutes = currentMinutes,
        onOpenSchedule = onOpenSchedule,
        onOpenXuexiaotongSchedule = onOpenXuexiaotongSchedule,
        isInSemester = isInSemester,
        todayExams = todayExams,
        assignments = assignments,
        nowMillis = nowMillis,
        enabled = enabled
    )
}


@Composable
private fun RadiantAtAGlance(
    todayCourses: List<Course>,
    currentMinutes: Int,
    onOpenSchedule: () -> Unit,
    onOpenXuexiaotongSchedule: () -> Unit,
    isInSemester: Boolean,
    todayExams: List<HomeExam>,
    assignments: List<HomeAssignment>,
    nowMillis: Long,
    enabled: Boolean
) {
    val hero = resolveHomeHeroText(
        todayCourses, todayExams, currentMinutes, isInSemester, assignments, nowMillis
    )
    val headline = hero.headline
    val subtitle = hero.subtitle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable(
                        onClick = if (hero.target == HomeHeroTarget.XUEXIAOTONG_SCHEDULE) {
                            onOpenXuexiaotongSchedule
                        } else {
                            onOpenSchedule
                        }
                    )
                } else {
                    Modifier
                }
            )
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

/**
 * 考试、上课、作业共用同一条时间线：
 * 先看"正在进行"（考试优先于课），否则取最近的未来时间点——
 * 作业截止、下节课开始、下一场考试开始，谁先到上谁；同一分钟时截止优先。
 * 已逾期未交的作业不上屏。
 */
internal fun resolveHomeHeroText(
    todayCourses: List<Course>,
    todayExams: List<HomeExam>,
    currentMinutes: Int,
    isInSemester: Boolean,
    assignments: List<HomeAssignment> = emptyList(),
    nowMillis: Long = 0L
): HomeHeroText {
    val ongoingExam = todayExams.firstOrNull {
        currentMinutes >= it.startMinutes && currentMinutes < it.endMinutes
    }
    val currentCourse = todayCourses.find {
        currentMinutes in ScheduleSectionTimes.getCourseTimeRangeInMinutes(it)
    }
    val nextExam = todayExams.asSequence()
        .filter { it.startMinutes > currentMinutes }
        .minByOrNull { it.startMinutes }
    val nextCourse = todayCourses.asSequence()
        .map { it to ScheduleSectionTimes.getCourseTimeRangeInMinutes(it).first }
        .filter { (_, start) -> start > currentMinutes }
        .minByOrNull { (_, start) -> start }
    val nearestAssignment = assignments.asSequence()
        .filter { it.endTs > nowMillis }
        .minByOrNull { it.endTs }

    val assignmentMinutes = nearestAssignment?.let {
        currentMinutes + minutesUntil(it.endTs, nowMillis)
    }
    val courseStart = nextCourse?.second
    val examStart = nextExam?.startMinutes
    val assignmentWins = assignmentMinutes != null &&
        (courseStart == null || assignmentMinutes <= courseStart) &&
        (examStart == null || assignmentMinutes <= examStart)
    val courseWins = !assignmentWins && courseStart != null &&
        (examStart == null || courseStart <= examStart)
    val urgentAssignment = nearestAssignment?.takeIf { assignmentWins }
    val upcomingCourse = nextCourse?.takeIf { courseWins }

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

        urgentAssignment != null -> {
            val remaining = minutesUntil(urgentAssignment.endTs, nowMillis)
            HomeHeroText(
                headline = "最近的作业还有\n${formatCourseDuration(remaining)}截止",
                subtitle = urgentAssignment.title.ifBlank { urgentAssignment.courseName },
                target = HomeHeroTarget.XUEXIAOTONG_SCHEDULE
            )
        }

        upcomingCourse != null -> HomeHeroText(
            headline = "下节课是 ${upcomingCourse.first.name}",
            subtitle = "还有 ${formatCourseDuration(upcomingCourse.second - currentMinutes)}，" +
                "在 ${upcomingCourse.first.location}"
        )

        nextExam != null -> HomeHeroText(
            headline = "下一场考试是 ${nextExam.name}",
            subtitle = "还有 ${formatCourseDuration(nextExam.startMinutes - currentMinutes)}" +
                nextExam.location.takeIf { it.isNotBlank() }?.let { "，在 $it" }.orEmpty()
        )

        !isInSemester -> HomeHeroText(headline = "假期中", subtitle = "准备您自己的安排吧")
        else -> HomeHeroText(headline = "今日空闲", subtitle = "今天暂无课程安排")
    }
}

private fun minutesUntil(endTs: Long, nowMillis: Long): Int =
    (((endTs - nowMillis).coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()

private fun formatCourseDuration(durationMinutes: Int): String {
    val days = durationMinutes / (24 * 60)
    if (days > 0) {
        val hours = (durationMinutes % (24 * 60)) / 60
        return if (hours > 0) "${days}天${hours}小时" else "${days}天"
    }
    return when {
        durationMinutes % 60 == 0 -> "${durationMinutes / 60}小时整"
        durationMinutes > 60 -> "${durationMinutes / 60}小时${durationMinutes % 60}分钟"
        else -> "${durationMinutes}分钟"
    }
}
