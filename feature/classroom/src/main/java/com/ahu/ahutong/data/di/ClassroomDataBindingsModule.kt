package com.ahu.ahutong.data.di

import com.ahu.ahutong.data.classroom.AppFreeClassroomSource
import com.ahu.ahutong.data.classroom.FreeClassroomSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClassroomDataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindFreeClassroomSource(impl: AppFreeClassroomSource): FreeClassroomSource
}
