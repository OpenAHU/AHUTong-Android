package com.ahu.ahutong.data.schedule.di

import com.ahu.ahutong.data.schedule.AhuCacheScheduleLocalStore
import com.ahu.ahutong.data.schedule.AppScheduleWeekResolver
import com.ahu.ahutong.data.schedule.CrawlerScheduleSource
import com.ahu.ahutong.data.schedule.ScheduleCrawlerSource
import com.ahu.ahutong.data.schedule.ScheduleLocalStore
import com.ahu.ahutong.data.schedule.ScheduleRepository
import com.ahu.ahutong.data.schedule.ScheduleWeekResolver
import com.ahu.ahutong.data.schedule.internal.DefaultScheduleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleDataModule {
    @Binds @Singleton
    abstract fun bindScheduleRepository(impl: DefaultScheduleRepository): ScheduleRepository

    @Binds @Singleton
    abstract fun bindScheduleLocalStore(impl: AhuCacheScheduleLocalStore): ScheduleLocalStore

    @Binds @Singleton
    abstract fun bindScheduleCrawlerSource(impl: CrawlerScheduleSource): ScheduleCrawlerSource

    @Binds @Singleton
    abstract fun bindScheduleWeekResolver(impl: AppScheduleWeekResolver): ScheduleWeekResolver
}
