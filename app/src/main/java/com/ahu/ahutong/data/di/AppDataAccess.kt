package com.ahu.ahutong.data.di

import com.ahu.ahutong.AHUApplication
import com.ahu.ahutong.data.auth.AuthRepository
import com.ahu.ahutong.data.schedule.ScheduleRepository
import dagger.hilt.android.EntryPointAccessors

/**
 * Non-Hilt access to domain repositories (widgets, OkHttp authenticators).
 * Prefer constructor injection when inside Hilt components.
 */
object AppDataAccess {
    private fun entryPoint(): DataEntryPoint =
        EntryPointAccessors.fromApplication(
            AHUApplication.getApp(),
            DataEntryPoint::class.java,
        )

    fun scheduleRepository(): ScheduleRepository = entryPoint().scheduleRepository()

    fun authRepository(): AuthRepository = entryPoint().authRepository()
}
