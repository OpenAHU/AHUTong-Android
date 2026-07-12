package com.ahu.ahutong.data.campuscard

import com.ahu.ahutong.data.dao.AHUCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuCacheCampusCardLocalStore @Inject constructor() : CampusCardLocalStore {
    override fun isMockMode(): Boolean = AHUCache.getMockData()

    override fun isLoggedIn(): Boolean = AHUCache.isLogin()

    override fun getCachedBalance(): Double? = AHUCache.getCardBalance()

    override fun saveBalance(balance: Double) {
        AHUCache.saveCardBalance(balance)
    }
}
