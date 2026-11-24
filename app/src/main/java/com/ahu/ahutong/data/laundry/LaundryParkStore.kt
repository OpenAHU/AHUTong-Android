package com.ahu.ahutong.data.laundry

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上次选择的洗衣房园区。
 *
 * 按园区名存而不是下标：台账升级后园区顺序会变，下标失效会把人甩到别的园区。
 * 只用于宿舍匹配不到时兜底，以及在「一栋楼对应多个洗衣房」时记住用户选过的那栋。
 */
@Singleton
class LaundryParkStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun last(): String? = prefs().getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun save(parkName: String) {
        if (parkName.isBlank()) return
        prefs().edit().putString(KEY, parkName).apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "laundry_park"
        const val KEY = "last_park_name"
    }
}
