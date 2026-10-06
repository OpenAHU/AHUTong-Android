package com.ahu.ahutong.data

import com.ahu.ahutong.data.dao.AHUCache
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 食堂窗口映射表（终端码 → 窗口名）的本机读写。
 *
 * 这是纯公共数据（POS 终端号与窗口名，不含任何个人信息）。
 * 标注策略（2026-10-06 用户拍板）：不走 App 内众包——开发者实地踩点 + 熟人线下报号，
 * 手动进服务器后台维护；客户端只读（整体替换式同步）。本类只保留读与整表写入。
 */
object CanteenWindowStore {

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, String>>() {}.type

    fun all(): Map<String, String> {
        val raw = AHUCache.getCanteenWindowMapJson() ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, String>>(raw, mapType) }.getOrNull() ?: emptyMap()
    }

    fun nameOf(terminal: String): String? = all()[terminal]

    /** 服务器映射表整体替换（同步用）。 */
    fun saveAll(map: Map<String, String>) {
        AHUCache.saveCanteenWindowMapJson(gson.toJson(map))
    }
}
