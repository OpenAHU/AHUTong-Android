package com.ahu.ahutong.data.model

import java.io.Serializable

data class GradeStudentProfile(
    val id: String,            // 教务系统学生ID，如 "122850"
    val trainingType: String,  // 培养类型：主修/微专业/辅修
    val department: String,    // 专业院系
    val major: String          // 专业
) : Serializable {
    val displayName: String
        get() = "$major ($trainingType)"
}
