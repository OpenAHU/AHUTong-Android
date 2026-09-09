package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course

data class ScheduleRefreshResult(
    val schedule: List<Course>,
    val changed: Boolean,
    val fetchedAt: Long
)

internal object ScheduleSnapshotComparator {
    fun hasChanged(cached: List<Course>?, latest: List<Course>): Boolean {
        if (cached == null) return true
        if (cached.size != latest.size) return true
        return cached.groupingBy { it }.eachCount() != latest.groupingBy { it }.eachCount()
    }
}
