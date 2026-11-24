package com.ahu.ahutong.data.laundry

import com.ahu.ahutong.data.network.AhuHttp
import java.io.IOException
import java.util.concurrent.TimeUnit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * 龙河洗衣房：趣智校园（胖乖生活）只读接口。
 *
 * 只有「机房列表」和「机房内机器状态」两个接口，都不需要登录、不校验签名；
 * 门店（洗衣机 / 干衣机 / 洗鞋机）与机型编号在客户端固定，省掉两次探测请求。
 */
interface QiekjLaundryApi {

    @FormUrlEncoded
    @POST("appointNew/point/listByShopId")
    suspend fun points(
        @Field("shopId") shopId: String,
        @Field("machineTypeId") machineTypeId: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): QiekjEnvelope<List<QiekjPoint>>

    @FormUrlEncoded
    @POST("appointNew/near/newMachines")
    suspend fun machines(
        @Field("shopId") shopId: String,
        @Field("machineTypeId") machineTypeId: String,
        @Field("orgId") orgId: String,
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): QiekjEnvelope<QiekjMachinePage>

    companion object {
        const val BASE_URL = "https://userapi.qiekj.com/"

        /** 官方客户端 UA（服务端当前不校验，但换浏览器 UA 会被拦，固定更稳）。 */
        private const val USER_AGENT =
            "QEUser/1.148.0 (com.qiekj.user; build:303; Android; userChannel:android_app; " +
                "version:1.148.0) OkHttp/4.12.0"

        val API: QiekjLaundryApi by lazy {
            AhuHttp.retrofit(BASE_URL) {
                // 串行限速调用，超时放长，避免慢响应被误判失败后重试放大请求量
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(15, TimeUnit.SECONDS)
            }.create(QiekjLaundryApi::class.java)
        }
    }
}

/** 统一包络：code == 0 成功；`t` 是服务器时间（毫秒）。 */
data class QiekjEnvelope<T>(
    val code: Int? = null,
    val msg: String? = null,
    val data: T? = null,
    val t: Long? = null
)

/** 平台返回非 0 码时抛错，由上层降级成「刷新失败」而不是当成空数据。 */
fun <T> QiekjEnvelope<T>.requireData(): T? {
    if (code != 0) throw IOException("qiekj ${code}: ${msg.orEmpty()}")
    return data
}

/** 机房（点位）：龙河的点位名就是楼栋名（402栋 / 301幢洗衣房）。 */
data class QiekjPoint(
    val orgId: String? = null,
    val orgName: String? = null
)

data class QiekjMachinePage(val items: List<QiekjMachine>? = null)

/** 一台机器：`deviceWorkStatus` 10 空闲 / 20 使用中；30、40 实测无剩余时间，按不可用处理。 */
data class QiekjMachine(
    val goodsId: Long? = null,
    val goodsName: String? = null,
    val deviceWorkStatus: Int? = null,
    val endUseTimeValue: String? = null
)
