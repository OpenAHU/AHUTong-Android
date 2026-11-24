package com.ahu.ahutong.data.laundry

import com.ahu.ahutong.data.network.AhuHttp
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * 乐摇摇「当前占用」接口（实测记录见 skill traffic-capture-analysis/references/leyaoyao-laundry-api.md）。
 *
 * 关键事实：
 * - 鉴权是 JWT 请求头 `Authorization_Bar`，不是 cookie；不带 token 一律 401 `请登陆`。
 * - body 为 `{}` = 空闲；有 `endTime`/`surplusTime` 的订单 = 使用中（能拿到别人机器的订单）。
 * - `equipmentStatus` / `query-mainboard-washer-time` 都不能判占用（前者是健康度，
 *   后者对脉冲设备烘干机直接报错），所以只走这一个端点。
 * - 响应对 `openid` 等他人字段一律不建模，避免把它们带进日志或界面。
 *
 * 有效期只有 2 小时且不可续期，401 就是「token 过期」，调用方要引导用户重新接力。
 */
interface LaundryApi {

    @GET("gw/washing/customer/order/get-order-list-home-page")
    suspend fun currentOrder(
        @Header("Authorization_Bar") token: String,
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Header("Origin") origin: String = ORIGIN,
        @Header("Referer") referer: String = REFERER,
        @Query("equipmentValue") equipmentValue: String
    ): Response<LaundryEnvelope>

    companion object {
        const val BASE_URL = "https://m.leyaoyao.com/"
        private const val ORIGIN = "https://m.leyaoyao.com"
        private const val REFERER = "https://m.leyaoyao.com/pages/newWasher.html"

        /** 平台按 UA 分流：非微信 UA 在 H5 上会被拦，但接口本身只认 token，普通 UA 可用。 */
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        val API: LaundryApi by lazy {
            AhuHttp.retrofit(BASE_URL) {
                // 单台查询是轻量 GET；一次刷新会并发几十个，超时要短，避免拖住整屏
                connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            }.create(LaundryApi::class.java)
        }

        /**
         * 台账全表下载（openahu.org，静态 JSON，走原生 OkHttp 不必建 Retrofit 实例）。
         * 200 → body + ETag；304 → null；其余状态码/异常向上抛，调用方自行降级。
         */
        object TABLE {
            private val client = AhuHttp.plain()
                // 服务器在境外，校内网首包/TLS 握手常常 10s 以上，超时过短会一直拉不到台账
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            suspend fun request(etag: String?): TableDownload? = kotlinx.coroutines.withContext(
                kotlinx.coroutines.Dispatchers.IO
            ) {
                var last: java.io.IOException? = null
                repeat(2) {
                    try {
                        return@withContext downloadOnce(etag)
                    } catch (error: java.io.IOException) {
                        last = error
                    }
                }
                throw last ?: java.io.IOException("table download failed")
            }

            private fun downloadOnce(etag: String?): TableDownload? {
                val builder = okhttp3.Request.Builder()
                    .url(URL)
                    .header("User-Agent", USER_AGENT)
                if (!etag.isNullOrBlank()) builder.header("If-None-Match", etag)
                return client.newCall(builder.build()).execute().use { response ->
                    when {
                        response.code == 304 -> null
                        response.isSuccessful -> {
                            val tag = response.header("ETag")
                            // 没带 ETag 的 200 无法做增量，视为坏响应
                            if (tag.isNullOrBlank()) null else TableDownload(response.body!!.string(), tag)
                        }
                        else -> throw java.io.IOException("HTTP ${response.code}")
                    }
                }
            }

            private const val URL = "https://openahu.org/download/laundry/table.json"
            private const val USER_AGENT =
                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
    }
}

/** 网关统一包络：code == "0000000" 才算成功。 */
data class LaundryEnvelope(
    val code: String? = null,
    val message: String? = null,
    val body: LaundryOrder? = null
) {
    val isSuccess: Boolean get() = code == SUCCESS_CODE

    companion object {
        const val SUCCESS_CODE = "0000000"
    }
}

/**
 * 进行中的订单。空闲时服务器返回 `{}`，所有字段为 null。
 * 刻意不声明 openid —— 那是别人的微信标识，界面上也不该出现。
 */
data class LaundryOrder(
    val serviceName: String? = null,
    val durings: Int? = null,
    val price: Double? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val surplusTime: Long? = null,
    val orderStatus: Int? = null,
    val nowTime: Long? = null
)
