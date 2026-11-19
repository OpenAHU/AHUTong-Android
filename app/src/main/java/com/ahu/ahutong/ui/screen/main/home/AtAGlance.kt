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
import com.ahu.ahutong.core.storage.HomeHeroSlot
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.ui.components.tourTarget
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.monet.a1
import com.kyant.monet.withNight
import java.time.Instant
import java.time.ZoneId

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
    priority: List<HomeHeroSlot> = HomeHeroSlot.DEFAULT_ORDER,
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
        priority = priority,
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
    priority: List<HomeHeroSlot>,
    enabled: Boolean
) {
    val hero = resolveHomeHeroText(
        todayCourses, todayExams, currentMinutes, isInSemester, assignments, nowMillis, priority
    )
    val headline = hero.headline
    val subtitle = hero.subtitle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tourTarget("home_hero")
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
 * 考试、上课、作业共用一条用户可排序的上屏顺序：
 * 按 [priority] 逐项取第一个"存在"的候选，命中即上屏；顺序是绝对的，不再比较时间先后。
 * 已逾期未交的作业不参与候选。
 */
internal fun resolveHomeHeroText(
    todayCourses: List<Course>,
    todayExams: List<HomeExam>,
    currentMinutes: Int,
    isInSemester: Boolean,
    assignments: List<HomeAssignment> = emptyList(),
    nowMillis: Long = 0L,
    priority: List<HomeHeroSlot> = HomeHeroSlot.DEFAULT_ORDER
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

    for (slot in HomeHeroSlot.normalize(priority)) {
        val text = when (slot) {
            HomeHeroSlot.ONGOING_EXAM -> ongoingExam?.let {
                HomeHeroText(
                    headline = "正在考试 · ${it.name}",
                    subtitle = "距考试结束还有 ${formatCourseDuration(it.endMinutes - currentMinutes)}"
                )
            }

            HomeHeroSlot.ONGOING_COURSE -> currentCourse?.let {
                HomeHeroText(
                    headline = "正在上课 · ${it.name}",
                    subtitle = "距下课还有 ${
                        formatCourseDuration(
                            ScheduleSectionTimes.getCourseTimeRangeInMinutes(it).last - currentMinutes
                        )
                    }"
                )
            }

            HomeHeroSlot.ASSIGNMENT -> nearestAssignment?.let {
                HomeHeroText(
                    headline = "最近的作业\n还有${formatCourseDuration(minutesUntil(it.endTs, nowMillis))}截止",
                    subtitle = buildString {
                        if (it.courseName.isNotBlank()) {
                            append(it.courseName)
                            append("的作业")
                            append(" · ")
                        }
                        append(formatDeadline(it.endTs, nowMillis))
                    },
                    target = HomeHeroTarget.XUEXIAOTONG_SCHEDULE
                )
            }

            HomeHeroSlot.NEXT_COURSE -> nextCourse?.let { (course, start) ->
                HomeHeroText(
                    headline = "下节课是 ${course.name}",
                    subtitle = "还有 ${formatCourseDuration(start - currentMinutes)}，" +
                        "在 ${course.location}"
                )
            }

            HomeHeroSlot.NEXT_EXAM -> nextExam?.let {
                HomeHeroText(
                    headline = "下一场考试是 ${it.name}",
                    subtitle = "还有 ${formatCourseDuration(it.startMinutes - currentMinutes)}" +
                        it.location.takeIf { location -> location.isNotBlank() }
                            ?.let { location -> "，在 $location" }.orEmpty()
                )
            }
        }
        if (text != null) return text
    }

    return if (!isInSemester) {
        HomeHeroText(headline = "假期中", subtitle = "准备您自己的安排吧")
    } else {
        HomeHeroText(headline = "今日空闲", subtitle = "今天暂无课程安排")
    }
}

private fun minutesUntil(endTs: Long, nowMillis: Long): Int =
    (((endTs - nowMillis).coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()

private fun formatDeadline(endTs: Long, nowMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val deadline = Instant.ofEpochMilli(endTs).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val day = when (deadline.toLocalDate()) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "${deadline.monthValue}-${deadline.dayOfMonth}"
    }
    return "$day ${"%02d:%02d".format(deadline.hour, deadline.minute)}"
}

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
