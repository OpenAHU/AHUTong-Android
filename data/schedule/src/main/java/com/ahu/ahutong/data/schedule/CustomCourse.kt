package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course

/**
 * 用户手动添加的课程（教务课表里不存在的加课、晚自习等）。
 *
 * 只在本机保存，按「账号 + 学期」隔离；课程下可以挂多个上课时间，
 * 每个时间各自带星期、周次与节次，避免为同一门课重复创建多条记录。
 */
data class CustomCourse(
    val id: String = "",
    val name: String = "",
    val teacher: String? = null,
    val location: String? = null,
    val note: String? = null,
    val colorArgb: Int? = null,
    val slots: List<CustomCourseSlot> = emptyList()
) {

    /** 读到损坏/旧版数据时尽量救回可用字段；名称与时间都缺失就丢弃。 */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "UNNECESSARY_SAFE_CALL")
    fun normalized(): CustomCourse? {
        // Gson 在未保留泛型签名时会把 slots 元素还原成 LinkedTreeMap，字段缺失时还会留下 null；
        // 这里按运行时类型过滤，坏数据只丢条目，不能让整张课表跟着崩。
        val safeId = id?.trim().orEmpty().ifBlank { return null }
        val safeName = name?.trim().orEmpty().ifBlank { return null }
        val rawSlots: List<*> = slots ?: emptyList<Any>()
        val safeSlots = rawSlots
            .filterIsInstance<CustomCourseSlot>()
            .mapNotNull { it.normalized() }
        if (safeSlots.isEmpty()) return null
        return copy(
            id = safeId,
            name = safeName,
            teacher = teacher?.trim()?.takeIf(String::isNotBlank),
            location = location?.trim()?.takeIf(String::isNotBlank),
            note = note?.trim()?.takeIf(String::isNotBlank),
            colorArgb = colorArgb?.takeIf { it != 0 },
            slots = safeSlots
        )
    }
}

data class CustomCourseSlot(
    val weekday: Int = 1,
    val weekIndexes: List<Int> = emptyList(),
    /** 未使用自定义时间时由用户选择；使用自定义时间时以吸附结果为准（见 [resolvedSpan]）。 */
    val startSection: Int = 1,
    val endSection: Int = 1,
    val customStartMinutes: Int? = null,
    val customEndMinutes: Int? = null
) {

    val hasCustomTime: Boolean
        get() = customStartMinutes != null && customEndMinutes != null

    fun resolvedSpan(): SectionSpan? {
        val start = customStartMinutes
        val end = customEndMinutes
        if (start != null && end != null && start in 0..MAX_MINUTES && end in 0..MAX_MINUTES && end > start) {
            return CustomCourseMath.snapToSections(start, end)
        }
        val first = startSection.coerceIn(1, CustomCourseMath.MAX_SECTION)
        val last = endSection.coerceIn(first, CustomCourseMath.MAX_SECTION)
        return SectionSpan(first, last)
    }

    /** 界面展示用：只在用户写了自定义时间时返回钟点，否则仍按节次展示。 */
    fun displayClockRange(): String? {
        if (!hasCustomTime) return null
        val start = customStartMinutes ?: return null
        val end = customEndMinutes ?: return null
        if (start !in 0..MAX_MINUTES || end !in 0..MAX_MINUTES || end <= start) return null
        return "${CustomCourseMath.formatClockMinutes(start)}-${CustomCourseMath.formatClockMinutes(end)}"
    }

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun normalized(): CustomCourseSlot? {
        val rawWeeks: List<*> = weekIndexes ?: emptyList<Any>()
        val safeWeeks = rawWeeks
            .filterIsInstance<Int>()
            .filter { week -> week in 1..CUSTOM_COURSE_MAX_WEEK }
            .distinct()
            .sorted()
        if (safeWeeks.isEmpty()) return null
        val weekday = weekday.takeIf { it in 1..7 } ?: return null
        val customStart = customStartMinutes?.takeIf { it in 0..MAX_MINUTES }
        val customEnd = customEndMinutes?.takeIf { it in 0..MAX_MINUTES }
        val useCustom = customStart != null && customEnd != null && customEnd > customStart
        val first = startSection.coerceIn(1, CustomCourseMath.MAX_SECTION)
        val last = endSection.coerceIn(first, CustomCourseMath.MAX_SECTION)
        return copy(
            weekday = weekday,
            weekIndexes = safeWeeks,
            startSection = first,
            endSection = last,
            customStartMinutes = customStart.takeIf { useCustom },
            customEndMinutes = customEnd.takeIf { useCustom }
        )
    }

    private companion object {
        const val MAX_MINUTES = 24 * 60 - 1
    }
}

data class SectionSpan(
    val startSection: Int,
    val endSection: Int
) {
    val length: Int
        get() = endSection - startSection + 1
}

/** 自定义时间的吸附与课程映射（纯函数，便于 JVM 回归）。 */
object CustomCourseMath {

    const val MAX_SECTION = 13

    private val timetableMinutes: Map<Int, IntRange> by lazy {
        ScheduleSectionTimes.timetable.mapValues { (_, value) ->
            val first = parseClockMinutes(value.substringBefore('-')) ?: 0
            val last = parseClockMinutes(value.substringAfter('-')) ?: first
            first..last
        }
    }

    fun parseClockMinutes(clock: String?): Int? {
        val value = clock?.trim().orEmpty()
        val separator = value.indexOf(':')
        if (separator <= 0 || separator >= value.lastIndex) return null
        val hour = value.substring(0, separator).toIntOrNull() ?: return null
        val minute = value.substring(separator + 1).toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun formatClockMinutes(minutes: Int): String {
        val safe = minutes.coerceIn(0, 24 * 60 - 1)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }

    fun sectionStartMinutes(section: Int): Int? = timetableMinutes[section]?.first

    fun sectionEndMinutes(section: Int): Int? = timetableMinutes[section]?.last

    /**
     * 最近节数吸附：开始时间取开始时间最接近的节次，结束时间取结束时间最接近的节次；
     * 结果夹紧到「结束不早于开始」。这样自定义时间可以落在任意钟点，网格仍按节次排版。
     */
    fun snapToSections(startMinutes: Int, endMinutes: Int): SectionSpan {
        val safeStart = startMinutes.coerceIn(0, 24 * 60 - 1)
        val safeEnd = endMinutes.coerceIn(0, 24 * 60 - 1)
        val startSection = timetableMinutes.entries
            .minWithOrNull(compareBy<Map.Entry<Int, IntRange>> { kotlin.math.abs(it.value.first - safeStart) }.thenBy { it.key })
            ?.key ?: 1
        val endSection = timetableMinutes.entries
            .minWithOrNull(compareBy<Map.Entry<Int, IntRange>> { kotlin.math.abs(it.value.last - safeEnd) }.thenBy { it.key })
            ?.key ?: MAX_SECTION
        return if (endSection < startSection) {
            SectionSpan(startSection, startSection)
        } else {
            SectionSpan(startSection, endSection)
        }
    }
}

const val CUSTOM_COURSE_ID_PREFIX = "custom:"
const val CUSTOM_COURSE_MAX_WEEK = 20

fun Course.isCustomCourse(): Boolean = courseId?.startsWith(CUSTOM_COURSE_ID_PREFIX) == true

fun Course.customCourseId(): String? = courseId
    ?.takeIf { it.startsWith(CUSTOM_COURSE_ID_PREFIX) }
    ?.removePrefix(CUSTOM_COURSE_ID_PREFIX)
    ?.takeIf(String::isNotBlank)

object CustomCourseMapper {

    fun toCourses(custom: CustomCourse): List<Course> = custom.slots.mapNotNull { slot ->
        val span = slot.resolvedSpan() ?: return@mapNotNull null
        val weeks = slot.weekIndexes.distinct().sorted()
        if (weeks.isEmpty()) return@mapNotNull null
        Course().apply {
            name = custom.name
            setWeekday(slot.weekday.toString())
            setStartWeek(weeks.first().toString())
            setEndWeek(weeks.last().toString())
            weekIndexes = weeks
            setStartTime(span.startSection.toString())
            setLength(span.length.toString())
            clockRange = slot.displayClockRange()
            location = custom.location
            teacher = custom.teacher
            extra = custom.note
            courseId = CUSTOM_COURSE_ID_PREFIX + custom.id
        }
    }

    /** 把自定义课程并到教务课表后面；没有自定义课程时原样返回（含 null）。 */
    fun merge(base: List<Course>?, custom: List<CustomCourse>): List<Course>? {
        if (custom.isEmpty()) return base
        val merged = base.orEmpty() + custom.flatMap(::toCourses)
        return merged.ifEmpty { null }
    }
}
