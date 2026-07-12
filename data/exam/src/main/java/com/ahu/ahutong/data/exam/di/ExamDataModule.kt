package com.ahu.ahutong.data.exam.di

import com.ahu.ahutong.data.exam.ExamRepository
import com.ahu.ahutong.data.exam.internal.DefaultExamRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExamDataModule {
    @Binds
    @Singleton
    abstract fun bindExamRepository(impl: DefaultExamRepository): ExamRepository
}
