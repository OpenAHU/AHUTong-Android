package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 今日课表：只读缓存 + 当前周次/星期，和课表页同一套判定（周次、单双周、教学周）。
 * 不触发网络、不改课表配置；缓存缺失时引导用户打开课表页。
 */
class TodayScheduleCapability(
    private val config: () -> ScheduleConfigBean?,
    private val cached: () -> List<Course>?,
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "today_schedule",
        title = "今日课表",
        description = "查看今天有哪些课、在哪个教室。只读本地已缓存的课表。",
        parameters = JsonParser.parseString("""{"type":"object","properties":{},"additionalProperties":false}""")
            .asJsonObject,
        sensitivity = AssistantSensitivity.ACADEMIC,
        openRoute = "schedule",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val scheduleConfig = config()
            ?: return message("课表还没准备好，打开课表页刷新一次")
        if (!scheduleConfig.isInSemester) {
            return message("现在不在教学周，今天没有课程")
        }
        val courses = cached()
            ?: return message("课表还没缓存，打开课表页刷新一次")
        val today = courses
            .filter { course -> course.occursOn(scheduleConfig.weekDay, scheduleConfig.week) }
            .sortedBy { it.startTime ?: Int.MAX_VALUE }
        if (today.isEmpty()) {
            return message("今天没有课")
        }
        val lines = today.map { course ->
            buildString {
                append(course.clockRange?.takeIf { it.isNotBlank() } ?: periodText(course))
                append(" · ")
                append(course.name.orEmpty())
                course.location?.takeIf { it.isNotBlank() }?.let {
                    append(" @ ")
                    append(it)
                }
            }
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "今天有 ${today.size} 节课",
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
