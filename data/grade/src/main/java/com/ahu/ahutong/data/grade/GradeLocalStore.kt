package com.ahu.ahutong.data.grade

import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade

interface GradeLocalStore {
    fun isMockMode(): Boolean

    fun getCachedGrade(): Grade?

    fun getPerProfileGrades(): Map<String, Grade?>

    fun saveGrade(grade: Grade)

    fun getGpaRank(studentId: String): GpaRankInfo?

    fun saveGpaRank(studentId: String, info: GpaRankInfo)
}
