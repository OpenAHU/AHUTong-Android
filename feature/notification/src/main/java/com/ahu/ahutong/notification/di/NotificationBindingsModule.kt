package com.ahu.ahutong.notification.di

import com.ahu.ahutong.data.schedule.AppScheduleReminderCoordinator
import com.ahu.ahutong.data.schedule.ScheduleReminderCoordinator
import com.ahu.ahutong.data.settings.AppCourseReminderActions
import com.ahu.ahutong.ui.settings.CourseReminderActions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationBindingsModule {
    @Binds
    @Singleton
    abstract fun bindScheduleReminderCoordinator(
        impl: AppScheduleReminderCoordinator,
    ): ScheduleReminderCoordinator

    @Binds
    @Singleton
    abstract fun bindCourseReminderActions(
        impl: AppCourseReminderActions,
    ): CourseReminderActions
}
