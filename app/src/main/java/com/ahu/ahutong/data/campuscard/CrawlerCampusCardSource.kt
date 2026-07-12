package com.ahu.ahutong.data.campuscard

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.CrawlerDataSource
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.model.BathRoom
import com.ahu.ahutong.data.model.Card
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrawlerCampusCardSource @Inject constructor() : CampusCardCrawlerSource {
    private val crawler = CrawlerDataSource()

    override suspend fun fetchBalance(): AppResult<Card> {
        return try {
            val response = crawler.getCardMoney()
            if (response.isSuccessful && response.data != null) {
                AppResult.success(response.data)
            } else {
                AppResult.error(response.msg ?: "获取余额失败", code = response.code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取余额失败", t)
        }
    }

    override suspend fun fetchQrcode(): AppResult<String> {
        return try {
            val response = AdwmhApi.API.getQrcode()
            if (response.code == 10000 && response.`object`.isNotEmpty()) {
                AppResult.success(response.`object`)
            } else {
                AppResult.error(response.msg ?: "获取二维码失败", code = response.code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取二维码失败", t)
        }
    }

    override suspend fun fetchBathrooms(): AppResult<List<BathRoom>> {
        return try {
            val response = crawler.getBathRooms()
            if (response.isSuccessful) {
                AppResult.success(response.data ?: emptyList())
            } else {
                AppResult.error(response.msg ?: "获取浴室状态失败", code = response.code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "获取浴室状态失败", t)
        }
    }
}
