package com.ahu.ahutong.di

import com.ahu.ahutong.data.schedule.ScheduleSource
import com.ahu.ahutong.data.schedule.ScheduleReadModel
import com.ahu.ahutong.data.schedule.ScheduleHolidaySource
import com.ahu.ahutong.data.calendar.RepositoryScheduleHolidaySource
import com.ahu.ahutong.ui.state.CacheScheduleReadModel
import com.ahu.ahutong.data.schedule.ScheduleWeekConfig
import com.ahu.ahutong.ui.state.RepositoryScheduleSource
import com.ahu.ahutong.ui.state.RepositoryScheduleWeekConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 课表 feature 的接线：端口在 :data:schedule，实现留在 :app（仓库、教务协议、缓存）。
 *
 * 只有组合根同时认识两边——课表的 ViewModel 因此看不见协议客户端，也看不见缓存键。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleWiringModule {

    @Binds
    @Singleton
    abstract fun bindScheduleSource(implementation: RepositoryScheduleSource): ScheduleSource

    @Binds
    @Singleton
    abstract fun bindScheduleHolidaySource(
        implementation: RepositoryScheduleHolidaySource
    ): ScheduleHolidaySource

    @Binds
    @Singleton
    abstract fun bindScheduleWeekConfig(
        implementation: RepositoryScheduleWeekConfig
    ): ScheduleWeekConfig

    /** 后台组件只拿只读事实（小组件、提醒都靠它）。 */
    @Binds
    @Singleton
    abstract fun bindScheduleReadModel(implementation: CacheScheduleReadModel): ScheduleReadModel
}
