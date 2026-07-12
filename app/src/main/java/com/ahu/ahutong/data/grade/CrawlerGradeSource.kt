package com.ahu.ahutong.data.grade

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.CrawlerDataSource
import com.ahu.ahutong.data.crawler.SdkDataSource
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crawler/HTML grade paths. GPA rank still goes through SdkDataSource HTML parser.
 */
@Singleton
class CrawlerGradeSource @Inject constructor() : GradeCrawlerSource {
    private val crawler = CrawlerDataSource()
    private val sdkFallback = SdkDataSource()

    override suspend fun fetchGrade(): AppResult<Grade> {
        return try {
            val response = crawler.getGrade()
            if (response.isSuccessful && response.data != null) {
                AppResult.success(response.data)
            } else {
                AppResult.error(response.msg ?: "获取成绩失败", code = response.code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取成绩失败", t)
        }
    }

    override suspend fun fetchGpaRank(studentId: String): AppResult<GpaRankInfo> {
        return try {
            val response = sdkFallback.getGpaRankFromHtml(studentId)
            if (response.isSuccessful && response.data != null) {
                AppResult.success(response.data)
            } else {
                AppResult.error(response.msg ?: "获取成绩排名失败", code = response.code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取成绩排名失败", t)
        }
    }
}
