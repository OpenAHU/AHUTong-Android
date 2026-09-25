package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.data.schedule.ScheduleReadModel
import com.ahu.ahutong.data.schedule.SemesterKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ScheduleReadModel] 的生产实现：全部转给 AHUCache 的读方法。
 *
 * 刻意只包读：后台组件拿不到任何写入口，因此"冷启动只画缓存"这条纪律由类型保证，
 * 而不是靠约定。
 */
@Singleton
class CacheScheduleReadModel @Inject constructor() : ScheduleReadModel {

    override fun canUseUndergraduateAcademics(): Boolean = AHUCache.canUseUndergraduateAcademics()

    override fun cachedSchedule(schoolTerm: String): List<Course>? =
        AHUCache.getSchedule(schoolTerm)

    override fun cachedScheduleFetchedAt(): Long? =
        AHUCache.getSchoolTerm()?.let(AHUCache::getScheduleFetchedAt)

    override fun schoolTermStartTime(schoolYear: String, schoolTerm: String): String? =
        AHUCache.getSchoolTermStartTime(schoolYear, schoolTerm)

    override fun currentSchoolTerm(): String? = AHUCache.getSchoolTerm()

    override fun cachedSemesterKey(): SemesterKey? =
        CurrentWeekResolver.getCachedSemesterKey()?.let {
            SemesterKey(raw = it.raw, schoolYear = it.schoolYear, schoolTerm = it.schoolTerm)
        }

    override fun cachedConfig(): ScheduleConfigBean =
        CurrentWeekResolver.resolveLocalConfig()?.config
            ?: CurrentWeekResolver.defaultConfig().config
}
