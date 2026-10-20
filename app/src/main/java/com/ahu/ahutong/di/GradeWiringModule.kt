package com.ahu.ahutong.di

import com.ahu.ahutong.data.grade.GradeSource
import com.ahu.ahutong.data.grade.ExamSource
import com.ahu.ahutong.core.common.MockDataSignals
import com.ahu.ahutong.ui.state.RepositoryExamSource
import com.ahu.ahutong.ui.state.RepositoryGradeSource
import com.ahu.ahutong.ui.state.GradeDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 成绩 feature 的接线：端口在 :data:grade，实现留在 :app（仓库与缓存）。
 *
 * 学年与学期不在这里——它由 :data:schedule 的 ScheduleWeekConfig 提供，
 * 界面从各自的端口取同一份事实。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GradeWiringModule {

    @Binds
    @Singleton
    abstract fun bindGradeSource(implementation: RepositoryGradeSource): GradeSource

    /** 假数据信号由成绩侧的适配器提供：它与成绩缓存共用同一套调试开关。 */
    @Binds
    @Singleton
    abstract fun bindMockDataSignals(implementation: RepositoryGradeSource): MockDataSignals

    @Binds
    @Singleton
    abstract fun bindExamSource(implementation: RepositoryExamSource): ExamSource

    companion object {
        @Provides
        @GradeDispatcher
        fun provideGradeDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
