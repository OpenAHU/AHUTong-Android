package com.ahu.ahutong.data.portal

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse

/**
 * Network implementation for lost & found. Bound in `:app` over AdwmhApi.
 */
interface LostFoundRemoteSource {
    suspend fun fetchCampus(): AppResult<AllCampus>

    suspend fun fetchTypes(): AppResult<AllLostFoundType>

    suspend fun fetchList(
        pageNo: Int,
        pageSize: Int,
        state: Int,
    ): AppResult<LostFoundResponse>

    suspend fun publish(request: LostFoundPublishRequest): AppResult<Any>

    suspend fun delete(id: String): AppResult<Any>
}
