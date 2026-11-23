package com.ahu.ahutong.data.model

/**
 * 校区 / 楼栋 / 楼层 / 房间的通用选项数据项。
 *
 * 原先声明在 ui.state.ElectricityDepositViewModel 中，但 data.model.RoomSelectionInfo 也引用它，
 * 形成 "模型 -> UI" 的反向依赖；抽出到模型模块后该反向依赖消失（见 AGENTS.md §2 R1）。
 */
data class CampusDataItem(
    val name: String,
    val value: String
)
