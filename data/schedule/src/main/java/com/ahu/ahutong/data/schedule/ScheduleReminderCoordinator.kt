package com.ahu.ahutong.data.schedule

/**
 * Reschedules course reminder alarms after schedule/config changes.
 * Bound in `:app` over CourseReminderScheduler.
 */
interface ScheduleReminderCoordinator {
    fun reschedule()
}
