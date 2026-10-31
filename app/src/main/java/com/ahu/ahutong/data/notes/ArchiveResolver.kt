package com.ahu.ahutong.data.notes

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.ScheduleSectionTimes

/** 自动归档需要的最小课程信息，便于脱离 `Course` 单独测试。 */
data class ArchiveSlot(
    val courseName: String,
    val weekday: Int,
    val weekIndexes: Set<Int>,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int
)

data class ArchiveTarget(
    val courseName: String,
    /** 命中时采用的时间窗（含缓冲），记录在照片元数据里用于回溯。 */
    val window: IntRange
)

/** 课前缓冲：提前到教室、课前拍板书都算这门课。 */
const val ARCHIVE_BEFORE_MINUTES = 10

/** 课后缓冲：拖堂、下课再补拍一张仍归这门课。 */
const val ARCHIVE_AFTER_MINUTES = 15

/**
 * 判定此刻该归到哪门课。
 *
 * 规则（见 docs/camera-notes.md A1~A4）：只有**唯一命中**才自动归档；
 * 不在学期内、节假日停课、多门重叠、没有课表数据，一律返回 null（落"未归档"），
 * 宁可让用户事后改归档，也不要把照片塞进错的科目。
 */
fun resolveArchiveTarget(
    nowMinutesOfDay: Int,
    weekday: Int,
    week: Int,
    isInSemester: Boolean,
    holidayBlocksClasses: Boolean,
    slots: List<ArchiveSlot>,
    beforeMinutes: Int = ARCHIVE_BEFORE_MINUTES,
    afterMinutes: Int = ARCHIVE_AFTER_MINUTES
): ArchiveTarget? {
    if (!isInSemester || holidayBlocksClasses) return null
    if (nowMinutesOfDay !in 0..(24 * 60)) return null
    val hits = slots.asSequence()
        .filter { it.weekday == weekday && week in it.weekIndexes }
        .filter { nowMinutesOfDay in (it.startMinuteOfDay - beforeMinutes)..(it.endMinuteOfDay + afterMinutes) }
        .distinctBy { it.courseName }
        .toList()
    val hit = hits.singleOrNull() ?: return null
    return ArchiveTarget(
        courseName = hit.courseName,
        window = (hit.startMinuteOfDay - beforeMinutes)..(hit.endMinuteOfDay + afterMinutes)
    )
}

/** 把课表课程转成归档判定的输入；缺时间/缺星期（自定义课）时返回 null。 */
fun archiveSlotOf(course: Course): ArchiveSlot? {
    val name = course.name.orEmpty().trim()
    if (name.isEmpty()) return null
    val weekday = course.weekday
    if (weekday !in 1..7) return null
    val range = ScheduleSectionTimes.getCourseTimeRangeInMinutes(course)
    if (range.isEmpty()) return null
    return ArchiveSlot(
        courseName = name,
        weekday = weekday,
        weekIndexes = course.weekIndexes.orEmpty().toSet(),
        startMinuteOfDay = range.first,
        endMinuteOfDay = range.last
    )
}

/**
 * 课程名 → 科目名：去掉"（1-16周）""(东区3201教室)"这类周次/地点后缀。
 *
 * 只剥括号里带周次/地点语义的片段；像"高等数学（A）"这种真正的课程名后缀保持原样。
 */
private val SUBJECT_SUFFIX = Regex("[（(][^）)]*(周|教室|楼|班|节|地点)[^）)]*[）)]")

fun archiveSubjectName(courseName: String): String {
    val cleaned = SUBJECT_SUFFIX.replace(courseName, "").trim()
    return cleaned.ifEmpty { courseName.trim() }
}
