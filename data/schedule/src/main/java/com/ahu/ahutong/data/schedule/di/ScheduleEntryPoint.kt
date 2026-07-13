package com.ahu.ahutong.data.schedule.di

import com.ahu.ahutong.data.schedule.ScheduleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Non-Hilt access for widgets and other BroadcastReceivers.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduleEntryPoint {
    fun scheduleRepository(): ScheduleRepository
}
