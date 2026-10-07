package com.ahu.ahutong.data.canteen

import android.util.Log
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.data.network.AhuHttp
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 必吃榜服务器接口（契约文档：docs/canteen-server-api.md，2026-10-06 记录级模型版）。
 *
 * 隐私红线：上传的去标识交易为 {terminal, ts, amountCents, canteen}——
 * 不含任何用户/设备标识（连哈希都没有）；去重靠服务端 (POS, 秒, 金额) 唯一约束。
 * 绝不在任何请求里带学号/卡号/姓名/教务会话。
 */
interface CanteenApi {

    @GET("/api/canteen/window-map")
    suspend fun windowMap(@Query("sinceVersion") sinceVersion: Int? = null): WindowMapResponse

    @POST("/api/canteen/txns")
    suspend fun uploadTxns(@Body body: TxnsUpload): Response<Unit>

    @GET("/api/canteen/insights")
    suspend fun insights(
        @Query("period") period: String,
        @Query("limit") limit: Int = 50,
        @Query("canteens") canteens: String? = null,
        @Query("minTxns") minTxns: Int = 20
    ): InsightsResponse

    companion object {
        /** 80 口（Caddy 反代）；8000 口安全组规则已删除，公网不可达（2026-10-07 服务端 v1.4.0）。 */
        const val BASE_URL = "http://121.37.174.199/"

        val API: CanteenApi by lazy {
            AhuHttp.retrofit(BASE_URL) {
                // 只在本接口的客户端上加头；密钥构建期注入（local.properties，不落 git）
                addInterceptor { chain ->
                    val key = BuildConfig.CANTEEN_WRITE_KEY
                    val request = chain.request()
                    if (key.isBlank()) {
                        // 没配 key 时不加头，让服务端返回 401——比静默失败好排查
                        Log.w("CanteenApi", "CANTEEN_WRITE_KEY 未配置，写入请求会被服务端拒绝")
                        chain.proceed(request)
                    } else {
                        chain.proceed(
                            request.newBuilder().header("X-Api-Key", key).build()
                        )
                    }
                }
            }.create(CanteenApi::class.java)
        }
    }
}

/* ---------------- 映射表 ---------------- */

data class WindowMapResponse(
    val version: Int,
    val entries: List<WindowMapEntry>?
)

data class WindowMapEntry(
    val terminal: String,
    val name: String?,
    val canteen: String? = null,
    val floor: String? = null
)

/* ---------------- 去标识交易上传 ---------------- */

/** 一笔去标识交易。ts 秒级 "yyyy-MM-dd HH:mm:ss"，(terminal, ts, amountCents) 为服务端幂等键。 */
data class TxnEntry(
    val terminal: String,
    val ts: String,
    val amountCents: Long,
    val canteen: String? = null,
    /** 楼层（「一楼」）：客户端从账单商户文本提取，服务端透传进 insights 条目展示。 */
    val floor: String? = null
)

data class TxnsUpload(val txns: List<TxnEntry>)

/* ---------------- insights ---------------- */

data class InsightsResponse(
    val period: String? = null,
    val startDay: String? = null,
    val endDay: String? = null,
    val sample: InsightSample? = null,
    val confidence: InsightConfidence? = null,
    val ranking: List<InsightRankingItem>? = null,
    val canteenRanking: List<InsightCanteenItem>? = null,
    val segments: List<InsightSegment>? = null,
    /** 时段分布（几点最挤）。 */
    val hourly: List<InsightHourly>? = null,
    val trend: List<InsightTrendPoint>? = null,
    val superlatives: InsightSuperlatives? = null,
    val notes: List<String>? = null
)

data class InsightSample(
    val terminals: Int = 0,
    val days: Int = 0,
    val txns: Int = 0,
    val totalAmountCents: Long? = null
)

data class InsightConfidence(
    val level: String? = null,
    val note: String? = null,
    val txns: Int? = null
)

data class InsightRankingItem(
    val rank: Int,
    val terminal: String,
    /** null = 未命名窗口。 */
    val name: String?,
    val canteen: String?,
    val floor: String? = null,
    /** 正餐交易笔数（展示口径可作「人次」）。 */
    val txns: Int,
    val share: Double? = null,
    val amountCents: Long? = null,
    /** 人均（分），÷100 显示为元。 */
    val avgCentsPerTxn: Double? = null
)

data class InsightCanteenItem(
    val canteen: String,
    val txns: Int,
    val share: Double? = null
)

data class InsightSegment(
    val segment: String,
    val txns: Int,
    val share: Double? = null
)

data class InsightHourly(val hour: Int, val txns: Int, val share: Double? = null)

data class InsightTrendPoint(val day: String, val txns: Int)

data class InsightSuperlatives(
    val topWindow: InsightRankingItem? = null,
    val topCanteen: InsightCanteenItem? = null,
    val lunchTopWindow: InsightRankingItem? = null,
    val dinnerTopWindow: InsightRankingItem? = null,
    val mostConsistent: InsightRankingItem? = null,
    val leastPopular: InsightRankingItem? = null,
    val mostImproved: InsightRankingItem? = null,
    @SerializedName("peakDay") val peakDay: InsightPeakDay? = null,
    @SerializedName("busiestHour") val busiestHour: InsightHourly? = null
)

data class InsightPeakDay(
    val day: String,
    val terminal: String,
    val name: String? = null,
    val canteen: String? = null,
    val txns: Int
)
