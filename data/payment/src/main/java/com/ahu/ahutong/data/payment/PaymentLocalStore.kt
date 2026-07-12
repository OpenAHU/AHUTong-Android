package com.ahu.ahutong.data.payment

import com.ahu.ahutong.data.model.ElectricityChargeInfo
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.RoomSelectionInfo

/**
 * Local preferences used by payment UIs (phone, electricity room history).
 * App binds an implementation over [com.ahu.ahutong.data.dao.AHUCache].
 */
interface PaymentLocalStore {
    fun isMockMode(): Boolean

    fun getPhone(): String?

    fun savePhone(phone: String)

    fun getCurrentUserId(): String?

    fun getCurrentUserName(): String?

    fun getRoomSelection(): RoomSelectionInfo?

    fun saveRoomSelection(info: RoomSelectionInfo)

    fun getElectricityDepositHistory(): List<ElectricityDepositHistoryItem>

    fun saveElectricityDepositHistory(history: List<ElectricityDepositHistoryItem>)

    fun getElectricityChargeInfo(): ElectricityChargeInfo?

    fun saveElectricityChargeInfo(info: ElectricityChargeInfo)

    fun clearElectricityChargeInfo()
}
