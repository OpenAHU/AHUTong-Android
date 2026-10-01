package com.ahu.ahutong.appwidget

import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.crawler.gmis.PostgraduateScheduleRepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.AcademicAccountType
import com.ahu.ahutong.data.schedule.WidgetScheduleReadModel
import com.ahu.ahutong.data.schedule.WidgetScheduleSnapshot
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.state.CacheScheduleReadModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Singleton
class AppWidgetScheduleReadModel @Inject constructor(
    private val undergraduate: CacheScheduleReadModel
) : WidgetScheduleReadModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Foreground page refreshes and teaching-week edits only request a cached widget redraw.
        scope.launch {
            combine(PostgraduateScheduleRepository.instance.revision,
                AHUCache.postgraduateWeekUpdates()) { schedule, week -> schedule to week }
                .drop(1)
                .collect {
                    if (SessionStore.currentUser()?.academicAccountType == AcademicAccountType.POSTGRADUATE) {
                        WidgetUpdateScheduler.renderCached(AppEnvironmentHolder.context())
                    }
                }
        }
    }

    override fun cachedSnapshot(): WidgetScheduleSnapshot {
        val user = SessionStore.currentUser()
        if (user?.academicAccountType == AcademicAccountType.POSTGRADUATE) {
            val snapshot = GraduateWidgetSchedule.instance.cached(user)
            // Do not publish a previous account's snapshot if it changed during cache reads.
            if (SessionStore.currentUser() === user) return snapshot
            return WidgetScheduleSnapshot(emptyList(), undergraduate.cachedConfig(),
                unavailableMessage = "账号已变化，请刷新课表")
        }
        return WidgetScheduleSnapshot(
            courses = undergraduate.currentSchoolTerm()?.let(undergraduate::cachedSchedule).orEmpty(),
            config = undergraduate.cachedConfig(),
            fetchedAt = undergraduate.cachedScheduleFetchedAt()
        )
    }
}
