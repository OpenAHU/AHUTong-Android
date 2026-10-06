package com.ahu.ahutong.data.canteen

import android.util.Log
import com.ahu.ahutong.data.CanteenWindowStore
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.recharge.analytics.toDeidentifiedTxns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 必吃榜服务器网关（契约：docs/canteen-server-api.md 记录级模型版）。
 *
 * - 上传闸门在调用侧（ViewModel 读 SettingsStore.canteenUploadConsent，未同意不调用本方法）。
 * - 上传内容：去标识逐笔交易（无用户/设备标识），分批 400 笔；服务端 (POS,秒,金额) 幂等，可放心重传。
 * - 映射表同步：sinceVersion 版本比对，变了全量替换本地缓存；失败沿用本地缓存（离线兜底）。
 * - insights：端侧 5 分钟缓存防筛选狂刷；forceRefresh=true 时绕过（进页面/手动刷新）。
 */
object CanteenGateway {

    private const val TAG = "CanteenGateway"
    private const val INSIGHTS_CACHE_MS = 5 * 60 * 1000L
    private const val UPLOAD_DAYS = 30L
    private const val BATCH_SIZE = 400

    suspend fun syncWindowMap(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val localVersion = AHUCache.getCanteenMapVersion()
            val resp = CanteenApi.API.windowMap(sinceVersion = localVersion)
            if (resp.version == localVersion) return@runCatching false
            val map = resp.entries.orEmpty()
                .mapNotNull { e -> e.name?.takeIf { it.isNotBlank() }?.let { e.terminal to it } }
                .toMap()
            CanteenWindowStore.saveAll(map)
            AHUCache.setCanteenMapVersion(resp.version)
            Log.i(TAG, "window map synced v$localVersion -> v${resp.version}, ${map.size} entries")
            true
        }.onFailure { Log.w(TAG, "window map sync failed, keep local cache", it) }
            .getOrDefault(false)
    }

    /** 上传最近 30 天去标识交易（调用侧已确认用户同意；幂等可重传；静默失败）。 */
    suspend fun uploadTxns(records: List<TurnoverRecord>): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val cutoff = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                .format(Date(System.currentTimeMillis() - UPLOAD_DAYS * 24 * 3600 * 1000))
            val txns = records.toDeidentifiedTxns()
                .filter { it.ts.substring(0, 10) >= cutoff }
                .map { TxnEntry(it.terminal, it.ts, it.amountCents, it.canteen) }
            if (txns.isEmpty()) return@runCatching
            var sent = 0
            txns.chunked(BATCH_SIZE).forEach { batch ->
                val resp = CanteenApi.API.uploadTxns(TxnsUpload(batch))
                if (!resp.isSuccessful) {
                    Log.w(TAG, "txns upload http ${resp.code()}")
                    return@forEach
                }
                sent += batch.size
            }
            Log.i(TAG, "txns uploaded: $sent/${txns.size}")
        }.onFailure { Log.w(TAG, "txns upload failed", it) }
    }

    @Volatile
    private var insightsCache: Pair<String, Pair<Long, InsightsResponse>>? = null

    suspend fun insights(
        period: String,
        limit: Int = 50,
        canteens: String? = null,
        minTxns: Int = 20,
        forceRefresh: Boolean = false
    ): InsightsResponse? = withContext(Dispatchers.IO) {
        val key = "$period|$canteens|$limit|$minTxns"
        if (!forceRefresh) {
            insightsCache?.let { (k, cached) ->
                if (k == key && System.currentTimeMillis() - cached.first < INSIGHTS_CACHE_MS) {
                    return@withContext cached.second
                }
            }
        }
        runCatching {
            CanteenApi.API.insights(period, limit, canteens, minTxns)
        }.onSuccess { insightsCache = key to (System.currentTimeMillis() to it) }
            .onFailure { Log.w(TAG, "insights failed", it) }
            .getOrNull()
    }
}
