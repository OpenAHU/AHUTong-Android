package com.ahu.ahutong.data.grade

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade

interface GradeRepository {
    suspend fun getGrade(isRefresh: Boolean = false): AppResult<Grade>

    suspend fun getGpaRank(studentId: String): AppResult<GpaRankInfo>
}
