package com.ahu.ahutong.data.xuexiaotong

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal object SyncThrottle {
    private val coursePeriod =
        Regex("""开课时间：(\d{4})-(\d{1,2})-(\d{1,2})\s*[～~]\s*(?:(\d{4})-(\d{1,2})-(\d{1,2}))?""")

    fun parseCoursePeriod(
        courseBlockHtml: String,
        zone: ZoneId = ZoneId.systemDefault()
    ): Pair<Long?, Long?> {
        val m = coursePeriod.find(courseBlockHtml) ?: return null to null
        fun date(year: String, month: String, day: String): Long? {
            return runCatching {
                LocalDate.of(year.toInt(), month.toInt(), day.toInt())
                    .atTime(LocalTime.MAX)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
        return date(m.groupValues[1], m.groupValues[2], m.groupValues[3]) to
            date(m.groupValues[4], m.groupValues[5], m.groupValues[6])
    }

    fun parseCourseStartTs(courseBlockHtml: String, zone: ZoneId = ZoneId.systemDefault()): Long? =
        parseCoursePeriod(courseBlockHtml, zone).first

    fun parseCourseEndTs(courseBlockHtml: String, zone: ZoneId = ZoneId.systemDefault()): Long? =
        parseCoursePeriod(courseBlockHtml, zone).second

    /** 课程区间与学期区间有交集即属于该学期；日期缺失时不误判为“不属于”。 */
    fun Course.overlapsSemester(range: LongRange?): Boolean {
        if (range == null) return true
        val end = endTs ?: return true
        val start = startTs ?: end
        return start <= range.last && end >= range.first
    }

    /**
     * 已完成作业不需要再进详情；未完成作业每次都要重抓，否则老师改期刷不出来。
     * 全量模式连已完成作业也重抓，用于补齐历史时间。
     */
    fun needsDeadlineFetch(work: Work, full: Boolean): Boolean = full || !work.isDone
}