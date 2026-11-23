package com.ahu.ahutong.data.laundry

/**
 * 洗衣房台账与占用状态的数据模型。
 *
 * 台账（园区 + 设备全表）来自 openahu.org 的线上 JSON（ETag/304 增量更新），
 * APK 里同时打包一份 assets 基线做断网兜底；占用状态必须联网 + token 才能拿到。
 */
enum class LaundryKind(val wire: String, val label: String) {
    WASHER("washingMachine", "洗衣机"),
    DRYER("dryer", "烘干机"),
    SHOE("shoeWasher", "洗鞋机");

    companion object {
        /** wire 用平台全名（washingMachine 等）；认不出的类型直接跳过该设备。 */
        fun of(wire: String?): LaundryKind? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * 一台机器。
 * - [slot] 机位号；洗鞋机没有 → null，标题直接显示类型名
 * - [location] 大洗衣房里的分区（11号楼 的 A1 / B8西 等；小园区为 null）
 * - [floor] 楼层（只有少数园区有，null 不显示）
 */
data class LaundryDevice(
    val id: String,
    val parkIndex: Int,
    val kind: LaundryKind,
    val location: String?,
    val floor: Int?,
    val slot: String?,
    val remark: String?,
    val online: Boolean
)

data class LaundryTable(
    val version: Int,
    val updatedAt: String,
    val parks: List<String>,
    val devices: List<LaundryDevice>
) {
    val isEmpty: Boolean get() = devices.isEmpty() || parks.isEmpty()

    companion object {
        val EMPTY = LaundryTable(0, "", emptyList(), emptyList())
    }
}

/**
 * 一台机器此刻的占用情况。
 *
 * 判定依据只有一条：`get-order-list-home-page` 的 body 是否是一张进行中的订单
 * （有 endTime / surplusTime）。`equipmentStatus` 是设备健康度，不是占用。
 */
sealed interface LaundryOccupancy {
    /** 空闲（接口 body 为空对象）。 */
    data object Idle : LaundryOccupancy

    /**
     * 使用中。[endAtMillis] 是**服务器时钟**下的结束时刻，
     * 本地倒计时用 [clockOffsetMillis]（服务器时间 - 本机时间）折算，避免手机时钟偏差。
     */
    data class InUse(
        val endAtMillis: Long,
        val clockOffsetMillis: Long,
        val serviceName: String?,
        val price: Double?
    ) : LaundryOccupancy

    /** 查不到（没 token、网络失败、设备不存在）。 */
    data class Unknown(val reason: String) : LaundryOccupancy
}
