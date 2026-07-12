package com.ahu.ahutong.data.campuscard

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.model.BathRoom
import com.ahu.ahutong.data.model.Card
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Campus-card crawler sink (Adwmh balance / QR).
 */
@Singleton
class CrawlerCampusCardSource @Inject constructor() : CampusCardCrawlerSource {
    override suspend fun fetchBalance(): AppResult<Card> {
        return try {
            val card = Card()
            card.balance = AdwmhApi.API.getBalance().`object`
            AppResult.success(card)
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
        // Historical crawler path returned empty; bathroom list is not on Adwmh.
        return AppResult.success(emptyList())
    }
}
