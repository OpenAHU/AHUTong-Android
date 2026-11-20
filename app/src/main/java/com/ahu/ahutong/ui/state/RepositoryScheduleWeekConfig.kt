package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.schedule.ConfigSource
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.data.schedule.ResolvedConfig
import com.ahu.ahutong.data.schedule.ScheduleWeekConfig
import com.ahu.ahutong.data.schedule.SemesterKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ScheduleWeekConfig] 的生产实现：转给 CurrentWeekResolver（问教务与校历）与 AHUCache（落盘）。
 *
 * 适配器负责把实现侧的形状翻成端口的形状（CurrentWeekResolver.Source → ConfigSource），
 * 于是课表的 ViewModel 既不需要认识教务协议，也不需要认识缓存的键名与观测日。
 */
@Singleton
class RepositoryScheduleWeekConfig @Inject constructor() : ScheduleWeekConfig {

    override fun cachedSemesterKey(): SemesterKey? =
        CurrentWeekResolver.getCachedSemesterKey()?.let {
            SemesterKey(raw = it.raw, schoolYear = it.schoolYear, schoolTerm = it.schoolTerm)
        }

    override fun storedSchoolYear(): String? = AHUCache.getSchoolYear()

    override fun currentWeekDay(): Int = CurrentWeekResolver.getCurrentWeekDay()

    override fun localSnapshot(): com.ahu.ahutong.data.model.ScheduleConfigBean? =
        CurrentWeekResolver.resolveLocalConfig()?.config

    override fun resolvedAtMillis(): Long? = AHUCache.getWeekResolvedAt()

    override fun markResolved(atMillis: Long) = AHUCache.saveWeekResolvedAt(atMillis)

    override fun buildSemesterKey(schoolYear: String, schoolTerm: String): String =
        CurrentWeekResolver.buildSemesterKey(schoolYear, schoolTerm)

    override fun isShowAllCourse(): Boolean = AHUCache.isShowAllCourse()

    /** 调度器由实现决定：这两个方法原先由调用方包在 Dispatchers.IO 里。 */
    override suspend fun resolveLocalFirst(): ResolvedConfig = withContext(Dispatchers.IO) {
        CurrentWeekResolver.resolveLocalFirst().toPortShape()
    }

    override suspend fun syncRemote(): ResolvedConfig? = withContext(Dispatchers.IO) {
        CurrentWeekResolver.syncRemoteConfig()?.toPortShape()
    }

    override suspend fun saveSchoolYearAndTerm(schoolYear: String, semesterKey: String) {
        AHUCache.saveSchoolYear(schoolYear)
        AHUCache.saveSchoolTerm(semesterKey)
    }

    override suspend fun saveTermPosition(
        schoolYear: String,
        schoolTerm: String,
        startTime: String,
        isInSemester: Boolean,
        observedOn: String
    ) {
        AHUCache.saveSchoolTermStartTime(schoolYear, schoolTerm, startTime)
        AHUCache.saveSchoolTermInSemester(
            schoolYear = schoolYear,
            schoolTerm = schoolTerm,
            isInSemester = isInSemester,
            observedOn = observedOn
        )
    }

    private fun CurrentWeekResolver.ResolvedConfig.toPortShape() = ResolvedConfig(
        config = config,
        source = when (source) {
            CurrentWeekResolver.Source.LOCAL -> ConfigSource.LOCAL
            CurrentWeekResolver.Source.REMOTE -> ConfigSource.REMOTE
            CurrentWeekResolver.Source.SERVER -> ConfigSource.SERVER
            CurrentWeekResolver.Source.DEFAULT -> ConfigSource.DEFAULT
        }
    )
}
