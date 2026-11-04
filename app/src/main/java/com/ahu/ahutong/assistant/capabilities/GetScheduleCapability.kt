package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.google.gson.JsonObject
import java.time.LocalDate

/**
 * 课表：只读本地缓存 + 当前周次/星期，和课表页同一套判定（周次、单双周、教学周）。
 * 不触发网络、不改课表配置；缓存缺失时引导用户打开课表页。
 */
class GetScheduleCapability(
    private val config: () -> ScheduleConfigBean?,
    private val cached: () -> List<Course>?,
    private val today: () -> LocalDate = LocalDate::now,
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_schedule",
        title = "查询课表",
        description = "查询从今天起指定天数内的课程安排。days 默认 7，范围 1-30。只读本地已缓存的课表。",
        parameters = schema("days" to intProp("查询天数，默认 7", minimum = 1, maximum = 30)),
        sensitivity = AssistantSensitivity.ACADEMIC,
        source = "教务系统",
        openRoute = "schedule",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val days = (arguments.intArg("days") ?: DEFAULT_DAYS).coerceIn(1, 30)
        val scheduleConfig = config()
            ?: return message("课表还没准备好，打开课表页刷新一次")
        if (!scheduleConfig.isInSemester) {
            return message("现在不在教学周，最近 $days 天没有课程")
        }
        val courses = cached()
            ?: return message("课表还没缓存，打开课表页刷新一次")

        val startDate = today()
        val lines = mutableListOf<String>()
        var count = 0
        for (offset in 0 until days) {
            val date = startDate.plusDays(offset.toLong())
            val weekday = date.dayOfWeek.value
            val week = scheduleConfig.week + ((scheduleConfig.weekDay - 1 + offset) / 7)
            val dayCourses = courses
                .filter { course -> course.occursOn(weekday, week) }
                .sortedBy { course -> course.startTime ?: Int.MAX_VALUE }
            dayCourses.forEach { course ->
                count++
                lines += buildString {
                    append(date.monthValue)
                    append("月")
                    append(date.dayOfMonth)
                    append("日 周")
                    append(WEEKDAY_LABELS[weekday - 1])
                    append(" · ")
                    append(course.clockRange?.takeIf { it.isNotBlank() } ?: periodText(course))
                    append(" · ")
                    append(course.name.orEmpty())
                    course.location?.takeIf { it.isNotBlank() }?.let {
                        append(" @ ")
                        append(it)
                    }
                }
            }
        }
        if (count == 0) {
            return message(if (days == 1) "今天没有课" else "最近 $days 天没有课")
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = if (days == 1) "今天有 $count 节课" else "最近 $days 天有 $count 节课",
                lines = lines,
                route = spec.openRoute,
            )
        )
    }

    private fun message(text: String): AssistantOutcome.Success =
        AssistantOutcome.Success(AssistantAnswer(summary = text, route = spec.openRoute))

    private fun periodText(course: Course): String {
        val start = course.startTime ?: return "上课时间未知"
        val length = course.length ?: 1
        val end = start + length - 1
        return if (end > start) "第 $start-$end 节" else "第 $start 节"
    }

    private companion object {
        const val DEFAULT_DAYS = 7
        val WEEKDAY_LABELS = charArrayOf('一', '二', '三', '四', '五', '六', '日')
    }
}

/** 与课表页一致的周次口径：优先 weekIndexes（含单双周），缺失时退回起止周。 */
internal fun Course.occursOn(weekday: Int, week: Int): Boolean {
    if ((this.weekday ?: 0) != weekday) return false
    val indexes = weekIndexes
    if (!indexes.isNullOrEmpty()) return indexes.contains(week)
    val start = startWeek ?: 0
    val end = endWeek ?: Int.MAX_VALUE
    return week in start..end
}
