package com.ahu.ahutong.data.grade.di

import com.ahu.ahutong.data.grade.AhuCacheGradeLocalStore
import com.ahu.ahutong.data.grade.CrawlerGradeSource
import com.ahu.ahutong.data.grade.GradeCrawlerSource
import com.ahu.ahutong.data.grade.GradeLocalStore
import com.ahu.ahutong.data.grade.GradeRepository
import com.ahu.ahutong.data.grade.internal.DefaultGradeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GradeDataModule {
    @Binds @Singleton
    abstract fun bindGradeRepository(impl: DefaultGradeRepository): GradeRepository

    @Binds @Singleton
    abstract fun bindGradeLocalStore(impl: AhuCacheGradeLocalStore): GradeLocalStore

    @Binds @Singleton
    abstract fun bindGradeCrawlerSource(impl: CrawlerGradeSource): GradeCrawlerSource
}
