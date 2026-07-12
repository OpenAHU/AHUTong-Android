package com.ahu.ahutong.data.campuscard.internal

import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.core.sdk.CampusNativeGateway
import com.ahu.ahutong.data.campuscard.CampusCardCrawlerSource
import com.ahu.ahutong.data.campuscard.CampusCardLocalStore
import com.ahu.ahutong.data.campuscard.CampusCardRepository
import com.ahu.ahutong.data.model.BathRoom
import com.ahu.ahutong.data.model.Card
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DefaultCampusCardRepository @Inject constructor(
    private val gateway: CampusNativeGateway,
    private val localStore: CampusCardLocalStore,
    private val crawlerSource: CampusCardCrawlerSource,
) : CampusCardRepository {

    override suspend fun getBalance(isRefresh: Boolean): AppResult<Card> =
        withContext(Dispatchers.IO) {
            if (!isRefresh && !localStore.isMockMode()) {
                localStore.getCachedBalance()?.let { cached ->
                    Log.d(TAG, "getBalance: cache hit")
                    return@withContext AppResult.success(
                        Card().apply { balance = cached },
                    )
                }
            }

            val remote = fetchRemoteBalance()
            if (remote is AppResult.Success) {
                remote.data.balance?.let { localStore.saveBalance(it) }
            }
            remote
        }

    override suspend fun getQrcode(): AppResult<String> = withContext(Dispatchers.IO) {
        if (gateway.isLocalServiceReady()) {
            when (val http = gateway.httpGetQrcode()) {
                is AppResult.Success -> {
                    when (val parsed = parseQrcodePayload(http.data)) {
                        is AppResult.Success -> return@withContext parsed
                        is AppResult.Error -> Log.w(TAG, "http qrcode parse failed: ${parsed.message}")
                    }
                }
                is AppResult.Error -> Log.w(TAG, "http qrcode failed: ${http.message}")
            }
        }

        if (gateway.isNativeLoaded()) {
            when (val jni = gateway.getQrcode()) {
                is AppResult.Success -> return@withContext jni
                is AppResult.Error -> Log.w(TAG, "jni qrcode failed: ${jni.message}")
            }
        }

        Log.d(TAG, "fallback to crawler qrcode")
        crawlerSource.fetchQrcode()
    }

    override suspend fun getBathrooms(): AppResult<List<BathRoom>> =
        withContext(Dispatchers.IO) {
            // Native path currently does not expose bathrooms; use crawler/app source.
            crawlerSource.fetchBathrooms()
        }

    private suspend fun fetchRemoteBalance(): AppResult<Card> {
        if (gateway.isLocalServiceReady()) {
            when (val http = gateway.httpGetBalance()) {
                is AppResult.Success -> return http
                is AppResult.Error -> Log.w(TAG, "http balance failed: ${http.message}")
            }
        }

        if (gateway.isNativeLoaded()) {
            when (val jni = gateway.getBalance()) {
                is AppResult.Success -> return jni
                is AppResult.Error -> Log.w(TAG, "jni balance failed: ${jni.message}")
            }
        }

        Log.d(TAG, "fallback to crawler balance")
        return crawlerSource.fetchBalance()
    }

    /**
     * Local HTTP returns a JSON envelope; JNI gateway already returns the code string.
     */
    private fun parseQrcodePayload(payload: String): AppResult<String> {
        // Already a bare QR payload (unlikely but cheap to accept).
        if (!payload.contains("\"code\"") && payload.isNotBlank()) {
            return AppResult.success(payload)
        }
        return try {
            val obj = JsonParser.parseString(payload).asJsonObject
            val code = obj.get("code")?.asInt ?: -1
            val msg = obj.get("msg")?.asString ?: "获取二维码失败"
            val value = obj.get("object")?.asString.orEmpty()
            if (code == 10000 && value.isNotEmpty()) {
                AppResult.success(value)
            } else {
                AppResult.error(msg, code = code)
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "二维码解析失败", t)
        }
    }

    private companion object {
        const val TAG = "CampusCardRepository"
    }
}
