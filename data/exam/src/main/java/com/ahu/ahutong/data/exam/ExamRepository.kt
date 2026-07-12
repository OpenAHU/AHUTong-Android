package com.ahu.ahutong.data.exam

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.Exam

interface ExamRepository {
    suspend fun getExamInfo(
        isRefresh: Boolean = false,
        studentId: String,
        studentName: String,
    ): AppResult<List<Exam>>
}
