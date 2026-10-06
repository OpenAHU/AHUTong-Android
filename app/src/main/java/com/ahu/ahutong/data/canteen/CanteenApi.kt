package com.ahu.ahutong.data.canteen

import com.ahu.ahutong.data.network.AhuHttp
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 必吃榜服务器接口（契约文档：docs/canteen-server-api.md）。
 *
 * 隐私红线：所有上传只含「窗口×日期」聚合量与窗口名建议，
 * 绝不携带学号/卡号/姓名/教务会话；匿名标识只有 X-Reporter-Token（随机 UUID，清数据即焚）。
 */
interface CanteenApi {

    @GET("/api/canteen/window-map")
    suspend fun windowMap(@Query("sinceVersion") sinceVersion: Int? = null): WindowMapResponse

    @POST("/api/canteen/stats")
    suspend fun uploadStats(
        @Header("X-Reporter-Token") reporterToken: String,
        @Body body: StatsUpload
    ): Response<Unit>

    @GET("/api/canteen/insights")
    suspend fun insights(
        @Query("period") period: String,
        @Query("limit") limit: Int = 50,
        @Query("canteens") canteens: String? = null,
        @Query("minMeals") minMeals: Int = 10
    ): InsightsResponse

    companion object {
        const val BASE_URL = "http://121.37.174.199:8000/"

        val API: CanteenApi by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(AhuHttp.plain().build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CanteenApi::class.java)
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

/* ---------------- 聚合上传 ---------------- */

/** 「窗口 × 日期」一行聚合量（服务端幂等：餐次更大者整行替换，可放心重传）。 */
data class StatsEntry(
    val terminal: String,
    /** YYYY-MM-DD */
    val period: String,
    val meals: Int,
    val mealsLunch: Int = 0,
    val mealsDinner: Int = 0,
    val mealsBreakfast: Int = 0,
    val amountCents: Long = 0,
    val canteen: String? = null
)

data class StatsUpload(val entries: List<StatsEntry>)

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
    val trend: List<InsightTrendPoint>? = null,
    val superlatives: InsightSuperlatives? = null,
    val notes: List<String>? = null
)

data class InsightSample(
    val terminals: Int = 0,
    val days: Int = 0,
    val totalMeals: Int = 0,
    val totalAmountCents: Long? = null
)

data class InsightConfidence(
    val level: String? = null,
    val note: String? = null,
    val contributors: Int? = null
)

data class InsightRankingItem(
    val rank: Int,
    val terminal: String,
    /** null = 未命名窗口。 */
    val name: String?,
    val canteen: String?,
    val floor: String? = null,
    val meals: Int,
    val share: Double? = null,
    val amountCents: Long? = null,
    val avgCentsPerMeal: Double? = null
)

data class InsightCanteenItem(
    val canteen: String,
    val meals: Int,
    val share: Double? = null
)

data class InsightSegment(
    val segment: String,
    val meals: Int,
    val share: Double? = null
)

data class InsightTrendPoint(val day: String, val meals: Int)

data class InsightSuperlatives(
    val topWindow: InsightRankingItem? = null,
    val topCanteen: InsightCanteenItem? = null,
    val lunchTopWindow: InsightRankingItem? = null,
    val dinnerTopWindow: InsightRankingItem? = null,
    val mostConsistent: InsightRankingItem? = null,
    val leastPopular: InsightRankingItem? = null,
    val mostImproved: InsightRankingItem? = null,
    /** 单日峰值（day + terminal + meals）。 */
    @SerializedName("peakDay") val peakDay: InsightPeakDay? = null
)

data class InsightPeakDay(
    val day: String,
    val terminal: String,
    val name: String? = null,
    val canteen: String? = null,
    val meals: Int
)
