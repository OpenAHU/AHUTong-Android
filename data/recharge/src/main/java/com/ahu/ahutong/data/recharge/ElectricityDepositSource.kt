package com.ahu.ahutong.data.recharge

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.ElectricityChargeInfo
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.DormitoryInfo
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.RoomSelectionInfo

/**
 * 电费充值的深接口：选项、房间详情与支付都以领域值表达。
 */
interface ElectricityDepositSource {

    suspend fun options(query: ElectricityOptionQuery): AhuResult<List<CampusDataItem>>

    suspend fun room(
        controller: ElectricityController,
        selection: RoomSelectionInfo
    ): AhuResult<ElectricityRoom>

    suspend fun pay(payment: ElectricityPayment): AhuResult<RechargeReceipt>

    /** 上次选的电控类型。 */
    fun selectedController(): ElectricityController

    /** 记住这次选的电控类型。 */
    fun saveController(controller: ElectricityController)

    /** 上次选的房间（含校区/楼栋/楼层）。 */
    fun roomSelection(): RoomSelectionInfo?

    /** 记住这次的房间选择。 */
    fun saveRoomSelection(selection: RoomSelectionInfo)

    /** 本机的缴费历史。 */
    fun depositHistory(): List<ElectricityDepositHistoryItem>

    fun saveDepositHistory(history: List<ElectricityDepositHistoryItem>)

    /** 本机缓存的电费账户信息。 */
    fun chargeInfo(): ElectricityChargeInfo?

    fun saveChargeInfo(info: ElectricityChargeInfo)

    /**
     * 本机保存的宿舍信息，用于进页面时预选楼栋。
     * 只用于选择，不参与计费；没有住宿信息时返回 null。
     */
    fun dormitoryInfo(): DormitoryInfo?
}
