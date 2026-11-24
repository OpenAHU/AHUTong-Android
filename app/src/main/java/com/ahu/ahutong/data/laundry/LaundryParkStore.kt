package com.ahu.ahutong.data.laundry

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上次选择的校区与洗衣房。
 *
 * 按名字存而不是下标：台账升级后园区顺序会变，下标失效会把人甩到别的园区。
 * 校区手选过一次就压过宿舍信息，直到用户再切回来；园区名按校区分开存，
 * 免得「磬苑的榴园」和「龙河的机房」互相覆盖。
 */
@Singleton
class LaundryParkStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** 手动切过的校区；null 表示没切过，按宿舍信息自动判断。 */
    fun campus(): LaundryCampus? = prefs().getString(KEY_CAMPUS, null)
        ?.let { name -> LaundryCampus.entries.firstOrNull { it.name == name } }

    fun saveCampus(campus: LaundryCampus) {
        prefs().edit().putString(KEY_CAMPUS, campus.name).apply()
    }

    fun last(campus: LaundryCampus, kind: LaundryKind = LaundryKind.WASHER): String? =
        prefs().getString(parkKey(campus, kind), null)?.takeIf { it.isNotBlank() }
            ?: prefs().getString(parkKey(campus), null)?.takeIf { it.isNotBlank() }
            ?: if (campus == LaundryCampus.QINGYUAN) legacyPark() else null

    fun save(campus: LaundryCampus, kind: LaundryKind, parkName: String) {
        if (parkName.isBlank()) return
        prefs().edit().putString(parkKey(campus, kind), parkName).apply()
    }

    /** 旧版只存一个园区名，升级后并到磬苑名下，老用户的选择不丢。 */
    private fun legacyPark(): String? =
        prefs().getString(KEY_LEGACY_PARK, null)?.takeIf { it.isNotBlank() }

    /**
     * 龙河的机房按机型分别记：洗鞋机的楼栋列表是从机器名里拆的，
     * 与洗衣机机房名会撞（都叫 300幢），共用一个键会把人甩到别的楼栋。
     */
    private fun parkKey(campus: LaundryCampus, kind: LaundryKind? = null) = buildString {
        append("last_park_name_").append(campus.name)
        if (kind != null && campus == LaundryCampus.LONGHE) append('_').append(kind.name)
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "laundry_park"
        const val KEY_CAMPUS = "selected_campus"
        const val KEY_LEGACY_PARK = "last_park_name"
    }
}
