package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.xuexiaotong.XuexiaotongSemesterRangeProvider
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学习通「本学期」的口径：只看月份，不取教务的学期起止日期。
 *
 * 2–7 月为春季学期、8 月–次年 1 月为秋季学期；2 月和 8 月本身就是寒暑假缓冲，
 * 正常教学会在 3 月和 9 月开始。
 */
@Singleton
class AppXuexiaotongSemesterRangeProvider @Inject constructor() : XuexiaotongSemesterRangeProvider {

    override suspend fun currentSemesterRange(): LongRange = rangeFor(LocalDate.now())

    internal fun rangeFor(today: LocalDate): LongRange {
        val year = today.year
        val (start, end) = when (today.monthValue) {
            in 2..7 -> LocalDate.of(year, 2, 1) to LocalDate.of(year, 7, 31)
            in 8..12 -> LocalDate.of(year, 8, 1) to LocalDate.of(year + 1, 1, 31)
            else -> LocalDate.of(year - 1, 8, 1) to LocalDate.of(year, 1, 31)
        }
        val zone = ZoneId.systemDefault()
        return start.atStartOfDay(zone).toInstant().toEpochMilli()..
            end.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
    }
}
