package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.core.common.map
import com.ahu.ahutong.core.common.onSuccess
import com.ahu.ahutong.appwidget.WidgetUpdateScheduler
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.CustomCourseMapper
import com.ahu.ahutong.data.schedule.CustomCourseStore
import com.ahu.ahutong.data.schedule.ScheduleRefreshResult
import com.ahu.ahutong.data.schedule.ScheduleSource
import com.ahu.ahutong.data.schedule.ScheduleSemester
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ScheduleSource] 的生产实现：把请求转给 AHURepository。
 *
 * 与 RepositoryScheduleWeekConfig 一样，适配器留在 :app，因为仓库要用协议客户端与缓存；
 * 课表的 ViewModel 因此只需要认识端口。
 *
 * 当前学期的自定义课程在读出时合并进来：教务缓存保持原样，
 * 刷新时不会因为自定义课程而判定「课表变了」，自定义课程也不会被远端结果冲掉。
 */
@Singleton
class RepositoryScheduleSource @Inject constructor(
    private val customCourses: CustomCourseStore
) : ScheduleSource {

    private fun withCustom(courses: List<Course>?): List<Course>? =
        CustomCourseMapper.merge(courses, customCourses.currentCourses())

    override fun usesMockData(): Boolean = AHUCache.getMockData()

    override fun cached(): List<Course>? = withCustom(AHURepository.getCachedSchedule())

    override fun fetchedAt(): Long? = AHURepository.getScheduleFetchedAt()

    override suspend fun fetch(isRefresh: Boolean): AhuResult<List<Course>> =
        AHURepository.getSchedule(isRefresh = isRefresh).map { withCustom(it) ?: it }.onSuccess {
            WidgetUpdateScheduler.renderCached(AppEnvironmentHolder.context())
        }

    override suspend fun refreshCache(): AhuResult<ScheduleRefreshResult> =
        AHURepository.refreshScheduleCache().map { result ->
            val merged = withCustom(result.schedule) ?: result.schedule
            result.copy(schedule = merged)
        }.onSuccess {
            WidgetUpdateScheduler.renderCached(AppEnvironmentHolder.context())
        }

    override suspend fun next(isRefresh: Boolean): AhuResult<List<Course>> =
        AHURepository.getNextSchedule(isRefresh = isRefresh)

    override suspend fun semesters(): AhuResult<List<ScheduleSemester>> =
        AHURepository.getScheduleSemesters()

    override suspend fun semester(semesterId: Int): AhuResult<List<Course>> =
        AHURepository.getSemesterSchedule(semesterId)
}

