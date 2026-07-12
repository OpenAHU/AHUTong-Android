package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.Course

/**
 * Android-side crawler fallback for schedule.
 * Bound in `:app` so `:data:schedule` does not depend on Retrofit crawler packages.
 */
interface ScheduleCrawlerSource {
    suspend fun fetchSchedule(): AppResult<List<Course>>

    suspend fun fetchNextSchedule(): AppResult<List<Course>>
}
