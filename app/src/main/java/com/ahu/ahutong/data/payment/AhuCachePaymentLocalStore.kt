package com.ahu.ahutong.data.payment

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.ElectricityChargeInfo
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.RoomSelectionInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuCachePaymentLocalStore @Inject constructor() : PaymentLocalStore {
    override fun isMockMode(): Boolean = AHUCache.getMockData()

    override fun getPhone(): String? = AHUCache.getPhone()

    override fun savePhone(phone: String) {
        AHUCache.savePhone(phone)
    }

    override fun getCurrentUserId(): String? = AHUCache.getCurrentUser()?.xh

    override fun getCurrentUserName(): String? = AHUCache.getCurrentUser()?.name

    override fun getRoomSelection(): RoomSelectionInfo? = AHUCache.getRoomSelection()

    override fun saveRoomSelection(info: RoomSelectionInfo) {
        AHUCache.saveRoomSelection(info)
    }

    override fun getElectricityDepositHistory(): List<ElectricityDepositHistoryItem> =
        AHUCache.getElectricityDepositHistory()

    override fun saveElectricityDepositHistory(history: List<ElectricityDepositHistoryItem>) {
        AHUCache.saveElectricityDepositHistory(history)
    }

    override fun getElectricityChargeInfo(): ElectricityChargeInfo? =
        AHUCache.getElectricityChargeInfo()

    override fun saveElectricityChargeInfo(info: ElectricityChargeInfo) {
        AHUCache.saveElectricityChargeInfo(info)
    }

    override fun clearElectricityChargeInfo() {
        AHUCache.clearElectricityChargeInfo()
    }
}
