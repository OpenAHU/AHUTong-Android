package com.ahu.ahutong.data.grade

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.ext.getSchoolYears
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuCacheGradeLocalStore @Inject constructor() : GradeLocalStore {
    override fun isMockMode(): Boolean = AHUCache.getMockData()

    override fun getCachedGrade(): Grade? = AHUCache.getGrade()

    override fun getPerProfileGrades(): Map<String, Grade?> = AHUCache.getPerProfileGrades()

    override fun saveGrade(grade: Grade) {
        AHUCache.saveGrade(grade)
    }

    override fun getGpaRank(studentId: String): GpaRankInfo? = AHUCache.getGpaRankInfo(studentId)

    override fun saveGpaRank(studentId: String, info: GpaRankInfo) {
        AHUCache.saveGpaRankInfo(studentId, info)
    }

    override fun getStudentProfiles(): List<GradeStudentProfile> =
        AHUCache.getGradeStudentProfiles()

    override fun getSchoolYears(): List<String>? =
        AHUCache.getCurrentUser()?.getSchoolYears()?.toList()
}
