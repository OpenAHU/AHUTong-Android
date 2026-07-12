package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.debug.DebugClock
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppScheduleWeekResolver @Inject constructor() : ScheduleWeekResolver {
    override fun getCachedSemesterKey(): ScheduleSemesterKey? =
        CurrentWeekResolver.getCachedSemesterKey()?.let {
            ScheduleSemesterKey(
                raw = it.raw,
                schoolYear = it.schoolYear,
                schoolTerm = it.schoolTerm,
            )
        }

    override fun buildSemesterKey(schoolYear: String, schoolTerm: String): String =
        CurrentWeekResolver.buildSemesterKey(schoolYear, schoolTerm)

    override fun getCurrentWeekDay(): Int = CurrentWeekResolver.getCurrentWeekDay()

    override fun isDebugClockMocked(): Boolean = DebugClock.isMocked()

    override fun nowCalendar(locale: Locale): Calendar = DebugClock.nowCalendar(locale)

    override fun nowDate(): Date = DebugClock.nowDate()

    override fun currentMinutes(locale: Locale): Int = DebugClock.currentMinutes(locale)

    override fun resolveLocalConfig(): ResolvedScheduleConfig? {
        val resolved = CurrentWeekResolver.resolveLocalConfig() ?: return null
        return ResolvedScheduleConfig(
            config = resolved.config,
            source = resolved.source.toDomain(),
        )
    }

    override suspend fun resolveLocalFirst(): ResolvedScheduleConfig {
        val resolved = CurrentWeekResolver.resolveLocalFirst()
        return ResolvedScheduleConfig(
            config = resolved.config,
            source = resolved.source.toDomain(),
        )
    }

    override suspend fun syncRemoteConfig(): ResolvedScheduleConfig? {
        val resolved = CurrentWeekResolver.syncRemoteConfig() ?: return null
        return ResolvedScheduleConfig(
            config = resolved.config,
            source = resolved.source.toDomain(),
        )
    }

    private fun CurrentWeekResolver.Source.toDomain(): ScheduleConfigSource = when (this) {
        CurrentWeekResolver.Source.LOCAL -> ScheduleConfigSource.LOCAL
        CurrentWeekResolver.Source.REMOTE -> ScheduleConfigSource.REMOTE
        CurrentWeekResolver.Source.DEFAULT -> ScheduleConfigSource.DEFAULT
    }
}
