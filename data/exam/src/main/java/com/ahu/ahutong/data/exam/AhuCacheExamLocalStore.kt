package com.ahu.ahutong.data.exam

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.Exam
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuCacheExamLocalStore @Inject constructor() : ExamLocalStore {
    override fun isMockMode(): Boolean = AHUCache.getMockData()

    override fun getCachedExams(): List<Exam> = AHUCache.getExamInfo().orEmpty()

    override fun saveExams(exams: List<Exam>) {
        AHUCache.saveExamInfo(exams)
    }

    override fun getCurrentUserId(): String? = AHUCache.getCurrentUser()?.xh

    override fun getCurrentUserName(): String? = AHUCache.getCurrentUser()?.name
}
