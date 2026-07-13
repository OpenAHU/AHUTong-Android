package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.core.common.AppContextHolder
import com.ahu.ahutong.notification.CourseReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppScheduleReminderCoordinator @Inject constructor() : ScheduleReminderCoordinator {
    override fun reschedule() {
        CourseReminderScheduler.reschedule(AppContextHolder.requireApp())
    }
}
