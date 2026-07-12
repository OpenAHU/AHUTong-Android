package com.ahu.ahutong.data.campuscard

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.BathRoom
import com.ahu.ahutong.data.model.Card

/**
 * Android crawler fallback for campus card. Bound in `:app`.
 */
interface CampusCardCrawlerSource {
    suspend fun fetchBalance(): AppResult<Card>

    suspend fun fetchQrcode(): AppResult<String>

    suspend fun fetchBathrooms(): AppResult<List<BathRoom>>
}
