package com.ahu.ahutong.data.portal

import com.ahu.ahutong.data.crawler.model.adwnh.CampusItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundTypeItem
import com.ahu.ahutong.data.dao.AHUCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuCacheLostFoundLocalStore @Inject constructor() : LostFoundLocalStore {
    override fun isMockMode(): Boolean = AHUCache.getMockData()

    override fun getCachedCampus(): List<CampusItem> = AHUCache.getLostFoundCampus()

    override fun saveCampus(items: List<CampusItem>) {
        AHUCache.saveLostFoundCampus(items)
    }

    override fun getCachedTypes(): List<LostFoundTypeItem> = AHUCache.getLostFoundType()

    override fun saveTypes(items: List<LostFoundTypeItem>) {
        AHUCache.saveLostFoundType(items)
    }

    override fun getCachedList(state: Int): List<LostFoundItem> = AHUCache.getLostFoundList(state)

    override fun saveList(state: Int, items: List<LostFoundItem>) {
        AHUCache.saveLostFoundList(state, items)
    }

    override fun appendList(state: Int, items: List<LostFoundItem>) {
        AHUCache.appendLostFoundList(state, items)
    }

    override fun clearList(state: Int) {
        AHUCache.clearLostFoundList(state)
    }
}
