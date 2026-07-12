package com.ahu.ahutong.data.campuscard

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.BathRoom
import com.ahu.ahutong.data.model.Card

/**
 * Campus card balance, payment QR, and bathroom status.
 */
interface CampusCardRepository {
    suspend fun getBalance(isRefresh: Boolean = false): AppResult<Card>

    suspend fun getQrcode(): AppResult<String>

    suspend fun getBathrooms(): AppResult<List<BathRoom>>
}
