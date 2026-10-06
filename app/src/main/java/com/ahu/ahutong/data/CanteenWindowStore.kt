package com.ahu.ahutong.data

import com.ahu.ahutong.data.dao.AHUCache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 食堂窗口映射表（终端码 → 窗口名）的本机读写。
 *
 * 这是纯公共数据（POS 终端号与窗口名，不含任何个人信息），
 * 由用户在自己的账单里顺手标注积累；后续可做内置表 + 热更新下发（V2）。
 */
object CanteenWindowStore {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, String>>() {}.type

    fun all(): Map<String, String> {
        val raw = AHUCache.getCanteenWindowMapJson() ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, String>>(raw, mapType) }.getOrNull() ?: emptyMap()
    }

    fun nameOf(terminal: String): String? = all()[terminal]

    fun rename(terminal: String, name: String) {
        val map = all().toMutableMap()
        if (name.isBlank()) map.remove(terminal) else map[terminal] = name.trim()
        AHUCache.saveCanteenWindowMapJson(gson.toJson(map))
    }
}
