package com.ahu.ahutong.data.portal

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdwmhLostFoundRemoteSource @Inject constructor() : LostFoundRemoteSource {
    override suspend fun fetchCampus(): AppResult<AllCampus> {
        return try {
            val campusList = AdwmhApi.API.getAllcampus()
            AppResult.success(campusList)
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取校区失败", t)
        }
    }

    override suspend fun fetchTypes(): AppResult<AllLostFoundType> {
        return try {
            val typeList = AdwmhApi.API.getAlllostfoundtype()
            AppResult.success(typeList)
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取类型失败", t)
        }
    }

    override suspend fun fetchList(
        pageNo: Int,
        pageSize: Int,
        state: Int,
    ): AppResult<LostFoundResponse> {
        return try {
            val list = AdwmhApi.API.getLostFoundList(pageNo, pageSize, state)
            AppResult.success(list)
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取失物招领列表失败", t)
        }
    }

    override suspend fun publish(request: LostFoundPublishRequest): AppResult<Any> {
        return try {
            val response = AdwmhApi.API.publishLostFound(request)
            if (response.isSuccessful) {
                AppResult.success(response.data ?: Any())
            } else {
                AppResult.error(response.msg ?: "发布失败", code = response.code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "发布失败", t)
        }
    }

    override suspend fun delete(id: String): AppResult<Any> {
        return try {
            val response = AdwmhApi.API.deleteLostFound(id)
            if (response.isSuccessful) {
                AppResult.success(response.data ?: Any())
            } else {
                AppResult.error(response.msg ?: "删除失败", code = response.code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "删除失败", t)
        }
    }
}
