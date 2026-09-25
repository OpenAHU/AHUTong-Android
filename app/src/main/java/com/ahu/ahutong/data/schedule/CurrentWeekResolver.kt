package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.fetchCurrentSemester
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.model.ScheduleConfigBean
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CurrentWeekResolver {

    enum class Source {
        LOCAL,
        REMOTE,
        DEFAULT
    }

    data class SemesterKey(
        val raw: String,
        val schoolYear: String,
        val schoolTerm: String
    )

    data class ResolvedConfig(
        val config: ScheduleConfigBean,
        val source: Source
    )

    fun buildSemesterKey(schoolYear: String, schoolTerm: String): String {
        return "${schoolYear.trim()}-${schoolTerm.trim()}"
    }

    fun parseSemesterKey(raw: String?): SemesterKey? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val normalized = raw.trim()
        val parts = normalized.split("-")
        return when {
            parts.size == 3 -> SemesterKey(
                raw = normalized,
                schoolYear = "${parts[0]}-${parts[1]}",
                schoolTerm = parts[2]
            )

            parts.size == 1 && AHUCache.getSchoolYear() != null -> {
                val schoolYear = AHUCache.getSchoolYear().orEmpty()
                SemesterKey(
                    raw = buildSemesterKey(schoolYear, normalized),
                    schoolYear = schoolYear,
                    schoolTerm = normalized
                )
            }

            else -> null
        }
    }

    fun getCachedSemesterKey(): SemesterKey? {
        return parseSemesterKey(AHUCache.getSchoolTerm())
    }

    fun getCurrentWeekDay(calendar: Calendar = DebugClock.nowCalendar(Locale.CHINA)): Int {
        return (calendar[Calendar.DAY_OF_WEEK] - 1).takeIf { it != 0 } ?: 7
    }

    fun resolveLocalConfig(now: LocalDate = DebugClock.nowLocalDate()): ResolvedConfig? {
        val semesterKey = getCachedSemesterKey() ?: return null
        val startTime = AHUCache.getSchoolTermStartTime(
            semesterKey.schoolYear,
            semesterKey.schoolTerm
        ) ?: return null
        val startDate = runCatching { LocalDate.parse(startTime) }.getOrNull() ?: return null
        val week = TeachingWeekPolicy.weekForDate(startDate, now)
        val cachedState = AHUCache.getSchoolTermInSemester(
            semesterKey.schoolYear,
            semesterKey.schoolTerm
        )
        val stateObservedOn = AHUCache.getSchoolTermInSemesterObservedOn(
            semesterKey.schoolYear,
            semesterKey.schoolTerm
        )?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val isInSemester = TeachingWeekPolicy.resolveLocalSemesterState(
            startDate = startDate,
            date = now,
            cachedState = cachedState,
            stateObservedOn = stateObservedOn
        )
        return ResolvedConfig(
            config = buildScheduleConfig(
                week = week,
                weekDay = now.dayOfWeek.value,
                startDate = startDate,
                isInSemester = isInSemester
            ),
            source = Source.LOCAL
        )
    }

    suspend fun resolveLocalFirst(now: LocalDate = DebugClock.nowLocalDate()): ResolvedConfig {
        val localConfig = resolveLocalConfig(now)
        if (DebugClock.isMocked()) {
            return localConfig ?: defaultConfig(now)
        }
        val cachedSemester = getCachedSemesterKey()
        val stateObservedOn = cachedSemester?.let {
            AHUCache.getSchoolTermInSemesterObservedOn(it.schoolYear, it.schoolTerm)
        }
        if (localConfig != null && stateObservedOn == now.toString()) return localConfig

        return runCatching { syncRemoteConfig(now) }.getOrNull()
            ?: localConfig
            ?: defaultConfig(now)
    }

    suspend fun syncRemoteConfig(now: LocalDate = DebugClock.nowLocalDate()): ResolvedConfig? {
        if (!AHUCache.canUseUndergraduateAcademics()) return null
        if (DebugClock.isMocked()) {
            return null
        }
        val response = JwxtApi.API.getCurrentTeachWeek()
        val semesterKey = parseSemesterKey(response.currentSemester)

        semesterKey?.let {
            AHUCache.saveSchoolYear(it.schoolYear)
            AHUCache.saveSchoolTerm(it.raw)
        } ?: AHUCache.saveSchoolTerm(response.currentSemester)

        val cachedStartDate = semesterKey?.let {
            AHUCache.getSchoolTermStartTime(it.schoolYear, it.schoolTerm)
                ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
        }
        val remoteIsInSemester = response.isInSemester && response.weekIndex > 0
        val officialStartDate = if (!remoteIsInSemester && semesterKey != null) {
            runCatching { getOfficialStartDate(semesterKey) }.getOrNull()
        } else {
            null
        }
        val position = TeachingWeekPolicy.resolveRemotePosition(
            now = now,
            remoteWeekIndex = response.weekIndex,
            remoteIsInSemester = response.isInSemester,
            cachedStartDate = cachedStartDate,
            officialStartDate = officialStartDate
        )

        semesterKey?.let {
            AHUCache.saveSchoolTermInSemester(
                schoolYear = it.schoolYear,
                schoolTerm = it.schoolTerm,
                isInSemester = position.isInSemester,
                observedOn = now.toString()
            )
            if (position.isInSemester || officialStartDate != null) {
                AHUCache.saveSchoolTermStartTime(
                    it.schoolYear,
                    it.schoolTerm,
                    position.startDate.toString()
                )
            }
        }

        return ResolvedConfig(
            config = buildScheduleConfig(
                week = position.week,
                weekDay = now.dayOfWeek.value,
                startDate = position.startDate,
                isInSemester = position.isInSemester
            ),
            source = Source.REMOTE
        )
    }

    fun defaultConfig(now: LocalDate = DebugClock.nowLocalDate()): ResolvedConfig {
        val weekDay = now.dayOfWeek.value
        val mondayOfCurrentWeek = now.minusDays((weekDay - 1).toLong())
        return ResolvedConfig(
            config = buildScheduleConfig(
                week = 1,
                weekDay = weekDay,
                startDate = mondayOfCurrentWeek,
                isInSemester = true
            ),
            source = Source.DEFAULT
        )
    }

    private fun buildScheduleConfig(
        week: Int,
        weekDay: Int,
        startDate: LocalDate,
        isInSemester: Boolean
    ): ScheduleConfigBean {
        return ScheduleConfigBean().apply {
            this.week = week
            this.weekDay = weekDay
            this.startTime = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
            this.isShowAll = AHUCache.isShowAllCourse()
            this.isInSemester = isInSemester
        }
    }

    private suspend fun getOfficialStartDate(semesterKey: SemesterKey): LocalDate? {
        val semester = fetchCurrentSemester()
        val officialKey = sequenceOf(semester.name, semester.code, semester.nameZh)
            .mapNotNull(::parseSemesterKey)
            .firstOrNull {
                it.schoolYear == semesterKey.schoolYear &&
                    it.schoolTerm == semesterKey.schoolTerm
            } ?: return null
        return LocalDate.of(
            semester.startDate.year,
            semester.startDate.monthOfYear,
            semester.startDate.dayOfMonth
        )
    }
}
