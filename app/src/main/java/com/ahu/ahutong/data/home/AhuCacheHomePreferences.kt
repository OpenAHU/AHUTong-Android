package com.ahu.ahutong.data.home

import com.ahu.ahutong.data.dao.AHUCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuCacheHomePreferences @Inject constructor() : HomePreferences {
    override fun getHomeWidgetSlots(): List<String?> = AHUCache.getHomeWidgetSlots()

    override fun saveHomeWidgetSlots(slots: List<String?>) {
        AHUCache.saveHomeWidgetSlots(slots)
    }

    override fun getWeatherShowOnHome(): Boolean = AHUCache.getWeatherShowOnHome()
}
