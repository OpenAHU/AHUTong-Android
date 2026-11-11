package com.ahu.ahutong.data.grade

import com.ahu.ahutong.core.common.MockDataSignals

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.data.model.Exam

/**
 * 考试数据的唯一入口。
 *
 * 界面问的是：有没有缓存的考试、缓存是什么时候取的、要不要拉一次。
 * 协议（教务考试页解析）与缓存分层都留在实现里（:app 的 AHURepository 与 AHUCache）。
 */
interface ExamSource : MockDataSignals {

    /** 缓存里的考试；没有则为空列表。 */
    fun cachedExams(): List<Exam>

    fun cachedProfiles(): List<GradeStudentProfile> = emptyList()

    /** 缓存取到的时间（epoch millis）；0 表示从没取过。 */
    fun cachedAt(): Long

    /** 拉取考试；[studentId] 与 [studentName] 是教务要求的查询参数。 */
    suspend fun fetchExams(
        isRefresh: Boolean,
        studentId: String,
        studentName: String
    ): AhuResult<List<Exam>>
}
