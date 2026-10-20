package com.ahu.ahutong.ui.screen.main.schedule

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.isCustomCourse

private val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun scheduleWeekdayLabel(weekday: Int): String =
    weekdayLabels.getOrNull(weekday - 1) ?: "周?"

/** 卡片底部胶囊：自定义课程优先显示自定义钟点，其余仍是地点。 */
fun Course.cardCapsuleText(isCurrentWeek: Boolean): String = when {
    !isCurrentWeek -> "非本周"
    isCustomCourse() && !clockRange.isNullOrBlank() -> clockRange!!.replace("-", "–")
    else -> location.shortScheduleLocation()
}

private val locationShortenMap = mapOf(
    "博学北楼" to "博北",
    "博学南楼" to "博南",
    "笃行南楼" to "笃南",
    "笃行北楼" to "笃北",
    "互联大楼" to "互楼",
    "体育场" to "体"
)

private val locationPattern = Regex(
    locationShortenMap.keys.joinToString("|") { Regex.escape(it) }
)

fun String?.shortScheduleLocation(): String {
    val shortened = orEmpty()
        .replace(locationPattern) { locationShortenMap[it.value].orEmpty() }
        .replace(Regex("\\s*\\[[^\\]]*]"), "")
        .replace(Regex("\\s+"), "")
        .trim()

    return shortened.ifBlank { "未知" }
}

fun Course.weekRangeText(): String {
    val weeks = weekIndexes.orEmpty()
        .filter { it > 0 }
        .distinct()
        .sorted()
    if (weeks.isEmpty()) {
        return when {
            startWeek == endWeek -> "${startWeek}周"
            startWeek > 0 && endWeek > 0 -> "$startWeek-${endWeek}周"
            else -> "周次未知"
        }
    }

    val first = weeks.first()
    val last = weeks.last()
    if (first == last) return "${first}周"

    val isContinuous = weeks.zipWithNext().all { (a, b) -> b - a == 1 }
    if (isContinuous) return "$first-${last}周"

    val isBiweekly = weeks.zipWithNext().all { (a, b) -> b - a == 2 }
    if (isBiweekly) {
        val parity = if (first % 2 == 0) "双" else "单"
        return "$first-$last${parity}周"
    }

    return weeks.joinToString("/") + "周"
}
