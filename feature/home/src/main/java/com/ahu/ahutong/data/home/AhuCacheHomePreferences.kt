package com.ahu.ahutong.data.home

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.weather.WeatherPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuCacheHomePreferences @Inject constructor() : HomePreferences, WeatherPreferences {
    override fun getHomeWidgetSlots(): List<String?> = AHUCache.getHomeWidgetSlots()

    override fun saveHomeWidgetSlots(slots: List<String?>) {
        AHUCache.saveHomeWidgetSlots(slots)
    }

    override fun getWeatherShowOnHome(): Boolean = AHUCache.getWeatherShowOnHome()

    override fun saveWeatherShowOnHome(enabled: Boolean) {
        AHUCache.saveWeatherShowOnHome(enabled)
    }

    override fun getWeatherAdcode(): String? = AHUCache.getWeatherAdcode()

    override fun saveWeatherAdcode(adcode: String) {
        AHUCache.saveWeatherAdcode(adcode)
    }
}
