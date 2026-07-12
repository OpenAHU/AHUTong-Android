package com.ahu.ahutong.data.schedule.di

import com.ahu.ahutong.data.schedule.ScheduleRepository
import com.ahu.ahutong.data.schedule.internal.DefaultScheduleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleDataModule {
    @Binds
    @Singleton
    abstract fun bindScheduleRepository(
        impl: DefaultScheduleRepository,
    ): ScheduleRepository
}
