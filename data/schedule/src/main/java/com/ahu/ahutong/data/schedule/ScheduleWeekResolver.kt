package com.ahu.ahutong.data.schedule

import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Resolves current teaching week / semester config for the schedule UI.
 * App binds an implementation over CurrentWeekResolver + DebugClock.
 */
interface ScheduleWeekResolver {
    fun getCachedSemesterKey(): ScheduleSemesterKey?

    fun buildSemesterKey(schoolYear: String, schoolTerm: String): String

    fun getCurrentWeekDay(): Int

    fun isDebugClockMocked(): Boolean

    fun nowCalendar(locale: Locale): Calendar

    fun nowDate(): Date

    fun currentMinutes(locale: Locale = Locale.CHINA): Int

    /** Local-only resolve; null when no cached term start is available. */
    fun resolveLocalConfig(): ResolvedScheduleConfig?

    suspend fun resolveLocalFirst(): ResolvedScheduleConfig

    suspend fun syncRemoteConfig(): ResolvedScheduleConfig?
}
