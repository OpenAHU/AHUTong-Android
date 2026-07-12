package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.Course

/**
 * Public schedule data API for UI / platform modules.
 */
interface ScheduleRepository {
    suspend fun getSchedule(isRefresh: Boolean = false): AppResult<List<Course>>

    suspend fun getNextSchedule(isRefresh: Boolean = false): AppResult<List<Course>>
}
