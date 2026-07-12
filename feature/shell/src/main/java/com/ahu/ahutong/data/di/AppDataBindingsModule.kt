package com.ahu.ahutong.data.di

import com.ahu.ahutong.data.classroom.AppFreeClassroomSource
import com.ahu.ahutong.data.classroom.FreeClassroomSource
import com.ahu.ahutong.data.schedule.AppScheduleReminderCoordinator
import com.ahu.ahutong.data.schedule.ScheduleReminderCoordinator
import com.ahu.ahutong.data.settings.AppCourseReminderActions
import com.ahu.ahutong.ui.settings.CourseReminderActions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-shell only bindings that still depend on notification / Application / mock source sets.
 * Domain sinks live in their respective :data / :feature modules.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppDataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindScheduleReminderCoordinator(
        impl: AppScheduleReminderCoordinator,
    ): ScheduleReminderCoordinator

    @Binds
    @Singleton
    abstract fun bindCourseReminderActions(impl: AppCourseReminderActions): CourseReminderActions

    @Binds
    @Singleton
    abstract fun bindFreeClassroomSource(impl: AppFreeClassroomSource): FreeClassroomSource
}
