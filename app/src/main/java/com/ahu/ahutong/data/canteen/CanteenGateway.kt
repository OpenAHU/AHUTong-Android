package com.ahu.ahutong.data.canteen

import android.util.Log
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.CanteenWindowStore
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.recharge.analytics.CanteenLabelCandidate
import com.ahu.ahutong.data.recharge.analytics.pickLabelCandidate
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
 * - 众包补标注：顺手从同一批账单里挑一个「未收录窗口」候选带回去给 UI（见 [uploadRecentBills]）。
 */
object CanteenGateway {

    private const val TAG = "CanteenGateway"
    private const val INSIGHTS_CACHE_MS = 5 * 60 * 1000L
    private const val UPLOAD_DAYS = 30L
    private const val BATCH_SIZE = 400
    private const val DAY_MS = 24 * 3600 * 1000L
    private const val BACKFILL_DAYS = 30L
    private const val SYNC_PAGE_SIZE = 100
    private const val MAX_SYNC_PAGES = 6
    private const val FETCH_MAX_ATTEMPTS = 3
    private const val FETCH_RETRY_DELAY_MS = 5000L

    /** 窗口名长度上限，与服务端 `NAME_MAX_LEN`（config.py）对齐，超了服务端会截断。 */
    const val WINDOW_NAME_MAX_LEN = 20

    /** 一次账单同步的结果：`ok` 供节流策略用，`ask` 为「要不要补标注」的候选（可为 null）。 */
    data class BillsSync(val ok: Boolean, val ask: CanteenLabelCandidate? = null)

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
     * 静默同步（冷启动一次 + 主页回前台节流触发，由 CanteenConsentGate 发起）。
     *
     * **客户端是笨蛋**（2026-10-07 用户拍板定稿）：每次触发固定拉最近 30 天账单、
     * 过滤后全量上传——没有增量窗口、没有上传标记、没有任何客户端侧去重。
     * 新数据还是老数据由服务端 (POS,秒,金额) 幂等键判定（重传 dupes 是正常态）。
     * 与热力图触发路径从此完全同构，不会再出现「两个入口两种包」。
     *
     * - 拉取失败原地重试（最多 [FETCH_MAX_ATTEMPTS] 次、间隔 [FETCH_RETRY_DELAY_MS]），
     *   兜住冷启动 token 未就绪/网络抖动
     * - 拉取或上传彻底失败 → 返回 false，调用侧 5 分钟后允许重触发
     *
     * @return `ok` = 拉取并上传成功（含窗口内本就零记录）；`ask` = 顺带挑出的补标注候选
     */
    suspend fun uploadRecentBills(handledMeals: Set<String> = emptySet()): BillsSync =
        withContext(Dispatchers.IO) {
            runCatching {
                val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                val now = System.currentTimeMillis()
                val timeFrom = tsFmt.format(Date(now - BACKFILL_DAYS * DAY_MS))
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
                    Log.w(TAG, "bill fetch failed after $FETCH_MAX_ATTEMPTS attempts")
                    return@withContext BillsSync(ok = false)
                }

                // 上传（30 天窗口内零记录时发空批，标记「查过了，没数据」）
                if (!uploadTxns(records)) {
                    Log.w(TAG, "upload incomplete")
                    return@withContext BillsSync(ok = false)
                }

                // 顺手挑候选：映射表先补齐，否则新装/久未同步的机器会把已收录窗口也当成未收录
                syncWindowMap()
                val ask = records.pickLabelCandidate(
                    knownTerminals = CanteenWindowStore.all().keys,
                    handled = handledMeals
                )
                Log.i(TAG, "background sync done: from=$timeFrom, fetched=${records.size}")
                BillsSync(ok = true, ask = ask)
            }.getOrElse {
                Log.w(TAG, "background bill sync failed", it)
                BillsSync(ok = false)
            }
        }

    /**
     * 众包补标注：上报窗口名。返回是否成功（失败仅影响这一顿，不改映射表）。
     *
     * 上报进服务端待审队列，**要人工采纳 + 发布**后才会同步回所有客户端。
     */
    suspend fun reportWindow(
        candidate: CanteenLabelCandidate,
        suggestedName: String,
        reporterToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            CanteenApi.API.windowReport(
                body = WindowReportBody(
                    terminal = candidate.terminal,
                    merchant = candidate.merchant,
                    suggestedName = suggestedName.trim(),
                    sampleCount = candidate.sampleCount
                ),
                reporterToken = reporterToken
            ).isSuccessful
        }
            .onFailure { Log.w(TAG, "window report failed", it) }
            .getOrDefault(false)
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
