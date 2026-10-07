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

    /* ---------------- 本地「终端→楼层」学习表 ----------------
     * 从用户自己的账单商户文本（「北二区食堂一楼」）提取，随上传流程顺手积累。
     * 用途：必吃榜条目服务端 floor 为空时的本地兜底（你去过的窗口都有楼层）。
     */

    fun learnedFloors(): Map<String, String> {
        val raw = AHUCache.getCanteenLearnedFloorsJson() ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, String>>(raw, mapType) }.getOrNull() ?: emptyMap()
    }

    fun learnedFloorOf(terminal: String): String? = learnedFloors()[terminal]

    /** 合并写入新学到的楼层（增量合并，不覆盖已有键为新值以外的内容）。 */
    fun saveLearnedFloors(new: Map<String, String>) {
        if (new.isEmpty()) return
        val merged = learnedFloors() + new
        AHUCache.saveCanteenLearnedFloorsJson(gson.toJson(merged))
    }
}
