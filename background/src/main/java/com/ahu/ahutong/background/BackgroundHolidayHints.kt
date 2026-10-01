package com.ahu.ahutong.background

import android.content.Context
import com.ahu.ahutong.data.schedule.ScheduleHoliday
import com.ahu.ahutong.data.schedule.ScheduleHolidaySource
import com.ahu.ahutong.data.schedule.scheduleHolidayYears
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** 日历请求失败时继续显示原课表/提醒；公开日历不能拖延课前通知。 */
internal suspend fun backgroundHolidayForDate(
    context: Context,
    date: LocalDate,
    refresh: Boolean = false
): ScheduleHoliday? = loadBackgroundHoliday(backgroundEntryPoint(context).scheduleHolidaySource(), date, refresh)

internal suspend fun loadBackgroundHoliday(
    source: ScheduleHolidaySource,
    date: LocalDate,
    refresh: Boolean = false
): ScheduleHoliday? = withTimeoutOrNull(2_000L) {
    try {
        source.load(scheduleHolidayYears(listOf(listOf(date))), refresh).valueOrNull()?.get(date)
            ?.takeIf { source.holidays.value[date] == it }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

internal fun backgroundCourseDate(startAtMillis: Long?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    startAtMillis?.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
