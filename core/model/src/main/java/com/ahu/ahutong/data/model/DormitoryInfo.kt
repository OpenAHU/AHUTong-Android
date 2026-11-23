package com.ahu.ahutong.data.model

import java.io.Serializable

/**
 * 智慧安大「住宿信息」（/tp_up/up/subgroup/getQinshiInfo）解析出的字段。
 *
 * 只保留有下游用途的字段（楼栋定位、宿舍号展示）；床位号等更细粒度信息不落盘。
 * 楼栋名为学校原始数据（如「榴园2号楼」），园区名可由 [DormitoryInfo.parkName] 推导。
 */
data class DormitoryInfo(
    /** 校区名，如「磬苑校区」。 */
    val campus: String = "",
    /** 楼栋全名，如「榴园2号楼」。权威楼栋来源。 */
    val building: String = "",
    /** 宿舍房间号，如「2439」。 */
    val room: String = "",
    /** 是否住宿，「1」为住宿。 */
    val resident: Boolean = false,
    /** 缓存写入时间（epoch 毫秒），用于展示与手动刷新提示。 */
    val cachedAt: Long = 0L
) : Serializable {

    /** 楼栋名推导园区名（「榴园2号楼」→「榴园」，「11号楼」→ null）。 */
    val parkName: String?
        get() = PARK_NAME_REGEX.find(building)?.value?.takeIf { it.isNotBlank() }

    companion object {
        private val PARK_NAME_REGEX = Regex("(磬苑|榴园|梅园|竹园|蕙园|桂花园|松园|桃园|杏园|李园|樱园|枫园|梓园|棠园|兰园)")

        /** 匹配失败时的宽松推导：任意「XX园」前缀。 */
        fun parkNameOf(buildingName: String): String? {
            PARK_NAME_REGEX.find(buildingName)?.let { return it.value }
            return Regex("([\u4e00-\u9fa5]{1,3}园)").find(buildingName)?.value
        }
    }
}
