package com.ahu.ahutong.data.xuexiaotong

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal object SyncThrottle {
    private val coursePeriod =
        Regex("""开课时间：(\d{4})-(\d{1,2})-(\d{1,2})\s*[～~]\s*(?:(\d{4})-(\d{1,2})-(\d{1,2}))?""")

    fun parseCourseEndTs(courseBlockHtml: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val m = coursePeriod.find(courseBlockHtml) ?: return null
        val year = m.groupValues[4].toIntOrNull() ?: return null
        val month = m.groupValues[5].toIntOrNull() ?: return null
        val day = m.groupValues[6].toIntOrNull() ?: return null
        return runCatching {
            LocalDate.of(year, month, day).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /**
     * 已完成作业不需要再进详情；未完成作业每次都要重抓，否则老师改期刷不出来。
     * 全量模式连已完成作业也重抓，用于补齐历史时间。
     */
    fun needsDeadlineFetch(work: Work, full: Boolean): Boolean = full || !work.isDone
}