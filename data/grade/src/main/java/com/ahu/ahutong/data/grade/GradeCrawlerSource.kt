package com.ahu.ahutong.data.grade

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade

/**
 * Android crawler / HTML paths for grades. Bound in `:app`.
 */
interface GradeCrawlerSource {
    suspend fun fetchGrade(): AppResult<Grade>

    suspend fun fetchGpaRank(studentId: String): AppResult<GpaRankInfo>
}
