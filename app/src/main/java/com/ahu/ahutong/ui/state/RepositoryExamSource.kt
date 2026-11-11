package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.grade.ExamSource
import com.ahu.ahutong.data.model.Exam
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ExamSource] 的生产实现：拉取转给 AHURepository，缓存与假数据信号转给 AHUCache 与 mock 设施。
 */
@Singleton
class RepositoryExamSource @Inject constructor() : ExamSource {

    override fun usesMockData(): Boolean = AHUCache.getMockData()

    override fun mockRefreshRevisions(): kotlinx.coroutines.flow.Flow<Long> =
        com.ahu.ahutong.data.mock.MockScenarioController.refreshRevisions()

    override fun cachedExams(): List<Exam> = AHUCache.getExamInfo().orEmpty()

    override fun cachedProfiles() = if (usesMockData()) emptyList() else AHUCache.getExamStudentProfiles()

    override fun cachedAt(): Long = AHUCache.getExamInfoUpdatedAt()

    override suspend fun fetchExams(
        isRefresh: Boolean,
        studentId: String,
        studentName: String
    ): AhuResult<List<Exam>> = AHURepository.getExamInfo(
        isRefresh = isRefresh,
        studentID = studentId,
        studentName = studentName
    )
}

