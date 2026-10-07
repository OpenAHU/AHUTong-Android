package com.ahu.ahutong.data.canteen

import android.util.Log
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.CanteenWindowStore
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.recharge.analytics.toDeidentifiedTxns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private const val DAY_MS = 24 * 3600 * 1000L
    private const val BACKFILL_DAYS = 30L
    /** 窗口向前重叠：兜上游入账延迟（迟到的记录带着原消费时间出现在账单里）。 */
    private const val OVERLAP_MS = DAY_MS
    private const val SYNC_PAGE_SIZE = 100
    private const val MAX_SYNC_PAGES = 6
    private const val FETCH_MAX_ATTEMPTS = 3
    private const val FETCH_RETRY_DELAY_MS = 5000L

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

    /**
     * 静默同步（冷启动一次 + 主页回前台节流触发，由 CanteenConsentGate 发起）：
     * 后台拉取「上次成功上传时刻 → 现在」的账单流水并上传去标识交易。
     *
     * **目标是每次触发都成功上传最新数据**：
     * - 拉取失败原地重试（最多 [FETCH_MAX_ATTEMPTS] 次、间隔 [FETCH_RETRY_DELAY_MS]），
     *   兜住冷启动 token 未就绪/网络抖动；彻底失败才发空载心跳并返回 false，
     *   由调用侧安排短间隔重触发
     * - 窗口向前重叠 1 天（兜上游入账延迟）；首次回补 30 天、封顶 30 天
     * - 时间戳级标记，只在全部成功后推进
     *
     * @return true = 拉取并上传成功（含窗口内本就零记录）
     */
    suspend fun uploadRecentBills(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            val now = System.currentTimeMillis()

            val lastTs = AHUCache.getCanteenLastUploadTs()
            val backfillFrom = now - BACKFILL_DAYS * DAY_MS
            // 起点 = 上次成功时刻 - 1 天重叠；无标记/窗口超 30 天则封顶到 30 天前
            val fromTs = if (lastTs == null) {
                backfillFrom
            } else {
                (lastTs - OVERLAP_MS).coerceAtLeast(backfillFrom)
            }
            val timeFrom = tsFmt.format(Date(fromTs))
            val timeTo = tsFmt.format(Date(now))

            // —— 拉取（原地重试：冷启动 token/网络未就绪是暂态，不是终态） ——
            val records = mutableListOf<TurnoverRecord>()
            var fetchOk = false
            var attempt = 0
            while (attempt < FETCH_MAX_ATTEMPTS && !fetchOk) {
                attempt++
                records.clear()
                fetchOk = fetchAllPages(records, timeFrom, timeTo)
                if (!fetchOk && attempt < FETCH_MAX_ATTEMPTS) {
                    Log.w(TAG, "bill fetch attempt $attempt failed, retrying")
                    delay(FETCH_RETRY_DELAY_MS)
                }
            }
            if (!fetchOk) {
                Log.w(TAG, "bill fetch failed after $FETCH_MAX_ATTEMPTS attempts, heartbeat only")
                uploadTxns(emptyList())   // 触发时间戳留在监控里
                return@withContext false
            }

            // 上传（空列表也会发空载心跳，见 uploadTxns）
            if (!uploadTxns(records)) {
                Log.w(TAG, "upload incomplete, keep marker for retry")
                return@withContext false
            }
            AHUCache.setCanteenLastUploadTs(now)
            Log.i(TAG, "background sync done: from=$timeFrom, fetched=${records.size}")
            true
        }.getOrElse {
            Log.w(TAG, "background bill sync failed", it)
            false
        }
    }

    /** 分页拉取一个时间窗的账单（不去重：服务端幂等键单点负责）。false = 任一页失败。 */
    private suspend fun fetchAllPages(
        out: MutableList<TurnoverRecord>,
        timeFrom: String,
        timeTo: String
    ): Boolean {
        var page = 1
        while (page <= MAX_SYNC_PAGES) {
            when (
                val result = AHURepository.getBillPage(
                    page = page, size = SYNC_PAGE_SIZE, timeFrom = timeFrom, timeTo = timeTo
                )
            ) {
                is AhuResult.Success -> {
                    val pageData = result.value
                    val rows = pageData.records.orEmpty()
                    out += rows
                    if (rows.isEmpty() || page >= (pageData.pages ?: 1)) return true
                    page++
                }
                is AhuResult.Failure -> return false
            }
        }
        return true
    }

    /**
     * 上传最近 30 天去标识交易（调用侧已确认用户同意）。返回是否全部成功。
     * **空载也 POST 一个空批次**：服务端数据包监控需要看到每次触发的请求时间戳。
     */
    suspend fun uploadTxns(records: List<TurnoverRecord>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cutoff = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                .format(Date(System.currentTimeMillis() - UPLOAD_DAYS * 24 * 3600 * 1000))
            val txns = records.toDeidentifiedTxns()
                .filter { it.ts.substring(0, 10) >= cutoff }
            // 顺手攒「终端→楼层」本地学习表（榜单条目服务端 floor 为空时的兜底；不发给服务器）
            CanteenWindowStore.saveLearnedFloors(
                txns.mapNotNull { t -> t.floor?.let { t.terminal to it } }.toMap()
            )
            var sent = 0
            var ok = true
            txns.map { TxnEntry(it.terminal, it.ts, it.amountCents, it.canteen, it.floor) }
                .chunked(BATCH_SIZE)
                .ifEmpty { listOf(emptyList()) }   // 空载心跳：监控可见
                .forEach { batch ->
                    val resp = CanteenApi.API.uploadTxns(TxnsUpload(batch))
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "txns upload http ${resp.code()}")
                        ok = false
                        return@forEach
                    }
                    sent += batch.size
                }
            Log.i(TAG, "txns uploaded: $sent/${txns.size}")
            ok
        }.onFailure { Log.w(TAG, "txns upload failed", it) }
            .getOrDefault(false)
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
