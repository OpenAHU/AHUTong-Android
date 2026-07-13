package com.ahu.ahutong.data.schedule

/**
 * Reschedules course reminder alarms after schedule/config changes.
 * Bound in `:feature:notification` over CourseReminderScheduler.
 */
interface ScheduleReminderCoordinator {
    fun reschedule()
}
