package com.ahu.ahutong.data.grade

import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile

interface GradeLocalStore {
    fun isMockMode(): Boolean

    fun getCachedGrade(): Grade?

    fun getPerProfileGrades(): Map<String, Grade?>

    fun saveGrade(grade: Grade)

    fun getGpaRank(studentId: String): GpaRankInfo?

    fun saveGpaRank(studentId: String, info: GpaRankInfo)

    fun getStudentProfiles(): List<GradeStudentProfile>

    /**
     * School years derived from the current user, or null when not logged in.
     */
    fun getSchoolYears(): List<String>?
}
