package com.ahu.ahutong.data.exam

import com.ahu.ahutong.data.model.Exam

interface ExamLocalStore {
    fun isMockMode(): Boolean

    fun getCachedExams(): List<Exam>

    fun saveExams(exams: List<Exam>)

    fun getCurrentUserId(): String?

    fun getCurrentUserName(): String?
}
