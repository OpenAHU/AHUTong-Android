package com.ahu.ahutong.data.schedule

/** 教务接口实际返回的学期；ID 不由学年或相邻学期推算。 */
data class ScheduleSemester(
    val id: Int,
    val name: String,
    val startDate: String? = null,
    val isCurrent: Boolean = false
)
