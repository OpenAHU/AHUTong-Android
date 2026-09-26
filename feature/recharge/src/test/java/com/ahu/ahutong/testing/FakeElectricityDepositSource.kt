package com.ahu.ahutong.testing

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.ElectricityChargeInfo
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityDepositSource
import com.ahu.ahutong.data.recharge.ElectricityOptionQuery
import com.ahu.ahutong.data.recharge.ElectricityPayment
import com.ahu.ahutong.data.recharge.ElectricityRoom
import com.ahu.ahutong.data.recharge.RechargeReceipt

class FakeElectricityDepositSource(
    private var controller: ElectricityController,
    private var selection: RoomSelectionInfo?,
    var optionsResult: AhuResult<List<CampusDataItem>>,
    var roomResult: AhuResult<ElectricityRoom>,
    var payResult: AhuResult<RechargeReceipt>
) : ElectricityDepositSource {

    val optionQueries = mutableListOf<ElectricityOptionQuery>()
    val payments = mutableListOf<ElectricityPayment>()
    val roomQueries = mutableListOf<Pair<ElectricityController, RoomSelectionInfo>>()
    private var history = emptyList<ElectricityDepositHistoryItem>()
    private var chargeInfo: ElectricityChargeInfo? = null

    override suspend fun options(query: ElectricityOptionQuery): AhuResult<List<CampusDataItem>> {
        optionQueries += query
        return optionsResult
    }

    override suspend fun room(
        controller: ElectricityController,
        selection: RoomSelectionInfo
    ): AhuResult<ElectricityRoom> {
        roomQueries += controller to selection
        return roomResult
    }

    override suspend fun pay(payment: ElectricityPayment): AhuResult<RechargeReceipt> {
        payments += payment
        return payResult
    }

    override fun selectedController(): ElectricityController = controller
    override fun saveController(controller: ElectricityController) {
        this.controller = controller
    }

    override fun roomSelection(): RoomSelectionInfo? = selection
    override fun saveRoomSelection(selection: RoomSelectionInfo) {
        this.selection = selection
    }

    override fun depositHistory(): List<ElectricityDepositHistoryItem> = history
    override fun saveDepositHistory(history: List<ElectricityDepositHistoryItem>) {
        this.history = history
    }

    override fun chargeInfo(): ElectricityChargeInfo? = chargeInfo
    override fun saveChargeInfo(info: ElectricityChargeInfo) {
        chargeInfo = info
    }
}
