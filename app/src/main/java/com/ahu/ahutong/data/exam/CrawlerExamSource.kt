package com.ahu.ahutong.data.exam

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.CrawlerDataSource
import com.ahu.ahutong.data.model.Exam
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrawlerExamSource @Inject constructor() : ExamCrawlerSource {
    private val crawler = CrawlerDataSource()

    override suspend fun fetchExams(studentId: String, studentName: String): AppResult<List<Exam>> {
        return try {
            val response = crawler.getExamInfo(studentId, studentName)
            if (response.isSuccessful) {
                AppResult.success(response.data ?: emptyList())
            } else {
                AppResult.error(response.msg ?: "获取考试信息失败", code = response.code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取考试信息失败", t)
        }
    }
}
