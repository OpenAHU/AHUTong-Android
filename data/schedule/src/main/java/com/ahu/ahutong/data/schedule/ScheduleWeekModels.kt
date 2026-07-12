package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.ScheduleConfigBean

enum class ScheduleConfigSource {
    LOCAL,
    REMOTE,
    DEFAULT,
}

data class ScheduleSemesterKey(
    val raw: String,
    val schoolYear: String,
    val schoolTerm: String,
)

data class ResolvedScheduleConfig(
    val config: ScheduleConfigBean,
    val source: ScheduleConfigSource,
)
