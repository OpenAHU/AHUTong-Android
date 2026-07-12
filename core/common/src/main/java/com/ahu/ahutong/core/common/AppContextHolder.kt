package com.ahu.ahutong.core.common

import android.app.Application

/**
 * Lightweight application context holder for core utilities
 * that need a global [Application] without depending on the app module.
 */
object AppContextHolder {
    @Volatile
    private var application: Application? = null

    fun init(app: Application) {
        application = app
    }

    fun requireApp(): Application =
        application ?: error("AppContextHolder is not initialized. Call init() in Application.onCreate().")

    fun getAppOrNull(): Application? = application
}
