package com.ahu.ahutong.data.calendar.di

import com.ahu.ahutong.data.calendar.AhuTongSchoolCalendarRemoteSource
import com.ahu.ahutong.data.calendar.SchoolCalendarRemoteSource
import com.ahu.ahutong.data.calendar.SchoolCalendarRepository
import com.ahu.ahutong.data.calendar.internal.DefaultSchoolCalendarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarDataModule {
    @Binds @Singleton
    abstract fun bindSchoolCalendarRepository(impl: DefaultSchoolCalendarRepository): SchoolCalendarRepository

    @Binds @Singleton
    abstract fun bindSchoolCalendarRemoteSource(impl: AhuTongSchoolCalendarRemoteSource): SchoolCalendarRemoteSource
}
