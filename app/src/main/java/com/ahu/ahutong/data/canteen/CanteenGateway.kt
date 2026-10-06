package com.ahu.ahutong.data.canteen

import android.util.Log
import com.ahu.ahutong.data.CanteenWindowStore
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.recharge.analytics.toDailyCanteenStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 必吃榜服务器网关（契约：docs/canteen-server-api.md）。
 *
 * - 映射表同步：sinceVersion 版本比对，变了就全量替换本地缓存（服务端语义如此）；
 *   拉取失败静默沿用本地缓存（离线兜底）。
 * - 聚合上传：只传「窗口 × 日期」聚合量 + 匿名 token；静默失败不打扰用户。
 * - insights：端侧 5 分钟内存缓存（服务端建议），防用户切筛选狂发请求。
 */
object CanteenGateway {

    private const val TAG = "CanteenGateway"
    private const val INSIGHTS_CACHE_MS = 5 * 60 * 1000L
    private const val UPLOAD_DAYS = 30L

    /** 映射表与服务器同步；返回是否有更新。 */
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

    /** 上传最近 30 天聚合量（幂等，可重传）。静默失败。 */
    suspend fun uploadStats(records: List<TurnoverRecord>): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val cutoff = dayFmt.format(
                Date(System.currentTimeMillis() - UPLOAD_DAYS * 24 * 3600 * 1000)
            )
            val entries = records.toDailyCanteenStats()
                .filter { it.day >= cutoff }
                .map {
                    StatsEntry(
                        terminal = it.terminal,
                        period = it.day,
                        meals = it.meals,
                        mealsLunch = it.mealsLunch,
                        mealsDinner = it.mealsDinner,
                        amountCents = it.amountCents,
                        canteen = it.canteen
                    )
                }
            if (entries.isEmpty()) return@runCatching
            val resp = CanteenApi.API.uploadStats(
                reporterToken = AHUCache.getOrCreateCanteenReporterToken(),
                body = StatsUpload(entries)
            )
            if (resp.isSuccessful) {
                Log.i(TAG, "stats uploaded: ${entries.size} rows")
            } else {
                Log.w(TAG, "stats upload http ${resp.code()}")
            }
        }.onFailure { Log.w(TAG, "stats upload failed", it) }
    }

    // —— insights 端侧缓存（key = period|canteens|limit|minMeals） ——
    @Volatile
    private var insightsCache: Pair<String, Pair<Long, InsightsResponse>>? = null

    suspend fun insights(
        period: String,
        limit: Int = 50,
        canteens: String? = null,
        minMeals: Int = 10
    ): InsightsResponse? = withContext(Dispatchers.IO) {
        val key = "$period|$canteens|$limit|$minMeals"
        insightsCache?.let { (k, cached) ->
            if (k == key && System.currentTimeMillis() - cached.first < INSIGHTS_CACHE_MS) {
                return@withContext cached.second
            }
        }
        runCatching {
            CanteenApi.API.insights(period, limit, canteens, minMeals)
        }.onSuccess { insightsCache = key to (System.currentTimeMillis() to it) }
            .onFailure { Log.w(TAG, "insights failed", it) }
            .getOrNull()
    }
}
