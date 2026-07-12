package com.ahu.ahutong.data.portal

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse

/**
 * Lost & found portal API.
 */
interface LostFoundRepository {
    suspend fun getAllCampus(forceRefresh: Boolean = false): AppResult<AllCampus>

    suspend fun getAllTypes(forceRefresh: Boolean = false): AppResult<AllLostFoundType>

    suspend fun getList(
        pageNo: Int = 1,
        pageSize: Int = 20,
        state: Int = 1,
    ): AppResult<LostFoundResponse>

    suspend fun publish(request: LostFoundPublishRequest): AppResult<Any>

    suspend fun delete(id: String): AppResult<Any>
}
