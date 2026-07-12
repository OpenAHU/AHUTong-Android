package com.ahu.ahutong.data.weather

/**
 * Weather display preferences. App binds over AHUCache / HomePreferences store.
 */
interface WeatherPreferences {
    fun getWeatherShowOnHome(): Boolean

    fun saveWeatherShowOnHome(enabled: Boolean)

    fun getWeatherAdcode(): String?

    fun saveWeatherAdcode(adcode: String)
}
