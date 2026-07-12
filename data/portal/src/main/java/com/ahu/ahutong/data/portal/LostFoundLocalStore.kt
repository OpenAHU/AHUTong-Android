package com.ahu.ahutong.data.portal

import com.ahu.ahutong.data.crawler.model.adwnh.CampusItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundTypeItem

interface LostFoundLocalStore {
    fun isMockMode(): Boolean

    fun getCachedCampus(): List<CampusItem>

    fun saveCampus(items: List<CampusItem>)

    fun getCachedTypes(): List<LostFoundTypeItem>

    fun saveTypes(items: List<LostFoundTypeItem>)

    fun getCachedList(state: Int): List<LostFoundItem>

    fun saveList(state: Int, items: List<LostFoundItem>)

    fun appendList(state: Int, items: List<LostFoundItem>)

    fun clearList(state: Int)
}
