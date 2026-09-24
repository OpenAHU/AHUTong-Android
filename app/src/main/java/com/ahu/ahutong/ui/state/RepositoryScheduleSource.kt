package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.core.common.onSuccess
import com.ahu.ahutong.appwidget.WidgetUpdateScheduler
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.ScheduleRefreshResult
import com.ahu.ahutong.data.schedule.ScheduleSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ScheduleSource] 的生产实现：把请求转给 AHURepository。
 *
 * 与 RepositoryScheduleWeekConfig 一样，适配器留在 :app，因为仓库要用协议客户端与缓存；
 * 课表的 ViewModel 因此只需要认识端口。
 */
@Singleton
class RepositoryScheduleSource @Inject constructor() : ScheduleSource {

    override fun usesMockData(): Boolean = AHUCache.getMockData()

    override fun cached(): List<Course>? = AHURepository.getCachedSchedule()

    override fun fetchedAt(): Long? = AHURepository.getScheduleFetchedAt()

    override suspend fun fetch(isRefresh: Boolean): AhuResult<List<Course>> =
        AHURepository.getSchedule(isRefresh = isRefresh).onSuccess {
            WidgetUpdateScheduler.renderCached(AppEnvironmentHolder.context())
        }

    override suspend fun refreshCache(): AhuResult<ScheduleRefreshResult> =
        AHURepository.refreshScheduleCache().onSuccess {
            WidgetUpdateScheduler.renderCached(AppEnvironmentHolder.context())
        }

    override suspend fun next(isRefresh: Boolean): AhuResult<List<Course>> =
        AHURepository.getNextSchedule(isRefresh = isRefresh)
}

