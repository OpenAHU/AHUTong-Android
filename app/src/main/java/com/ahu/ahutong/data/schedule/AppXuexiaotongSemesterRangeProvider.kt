package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.fetchCurrentSemester
import com.ahu.ahutong.data.crawler.model.jwxt.EndDate
import com.ahu.ahutong.data.crawler.model.jwxt.StartDate
import com.ahu.ahutong.data.xuexiaotong.XuexiaotongSemesterRangeProvider
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 教务当前学期区间：优先使用教务返回的官方起止日期；失败后退回周配置，
 * 再退回 3–8 月 / 9–次年 2 月的月份口径。按天缓存，避免重复请求。
 */
@Singleton
class AppXuexiaotongSemesterRangeProvider @Inject constructor() : XuexiaotongSemesterRangeProvider {

    private var cached: Pair<LocalDate, LongRange>? = null

    override suspend fun currentSemesterRange(): LongRange? {
        val today = LocalDate.now()
        cached?.let { (day, range) -> if (day == today) return range }
        val official = runCatching {
            val semester = fetchCurrentSemester(JwxtApi.API)
            val start = semester.startDate.toLocalDateOrNull()
            val end = semester.endDate.toLocalDateOrNull()
            if (start != null && end != null && !end.isBefore(start)) start to end else null
        }.getOrNull()
        val dates = official ?: fallbackSemesterDates(today) ?: return null
        val zone = ZoneId.systemDefault()
        val range = dates.first.atStartOfDay(zone).toInstant().toEpochMilli()..
            dates.second.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        cached = today to range
        return range
    }

    private fun fallbackSemesterDates(today: LocalDate): Pair<LocalDate, LocalDate>? {
        val config = runCatching { CurrentWeekResolver.resolveLocalConfig()?.config }.getOrNull()
        val startDate = config?.startTime?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
        if (startDate != null) {
            return startDate to startDate.plusWeeks(20).minusDays(1)
        }
        val year = today.year
        return when (today.monthValue) {
            in 3..8 -> LocalDate.of(year, 3, 1) to LocalDate.of(year, 8, 31)
            in 9..12 -> LocalDate.of(year, 9, 1) to LocalDate.of(year + 1, 3, 1).minusDays(1)
            else -> LocalDate.of(year - 1, 9, 1) to LocalDate.of(year, 3, 1).minusDays(1)
        }
    }

    private fun StartDate.toLocalDateOrNull(): LocalDate? =
        runCatching { LocalDate.of(year, monthOfYear, dayOfMonth) }.getOrNull()

    private fun EndDate.toLocalDateOrNull(): LocalDate? =
        runCatching { LocalDate.of(year, monthOfYear, dayOfMonth) }.getOrNull()
}
