package com.ahu.ahutong.data.home

/**
 * Home layout / display preferences.
 * App binds an implementation over [com.ahu.ahutong.data.dao.AHUCache].
 */
interface HomePreferences {
    fun getHomeWidgetSlots(): List<String?>

    fun saveHomeWidgetSlots(slots: List<String?>)

    fun getWeatherShowOnHome(): Boolean
}
