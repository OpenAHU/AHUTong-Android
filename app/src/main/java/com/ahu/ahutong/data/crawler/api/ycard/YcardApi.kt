package com.ahu.ahutong.data.crawler.api.ycard

import android.util.Log
import com.ahu.ahutong.data.network.NetworkLogging
import com.ahu.ahutong.data.network.campusCookies
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.crawler.manager.TokenManager
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.Token
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverCountResponse
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverResponse
import okhttp3.Interceptor
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Call
import com.ahu.ahutong.data.network.retrofit
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import com.ahu.ahutong.data.network.AhuHttp

interface YcardApi {

    @GET("/berserker-auth/cas/redirect/neusoftCas")
    fun login(
        @Query("targetUrl") targetUrl: String = "https://ycard.ahu.edu.cn/plat/?name=loginTransit"
    ): Call<ResponseBody>


    @GET("/berserker-app/ykt/tsm/queryCard")
    suspend fun loadCardRecharge(
        @Query("scene") scene: String = "cardRecharge",
        @Query("synAccessSource") synAccessSource: String = "h5",
    ): Response<CardInfo>

    /** 账单流水分页查询。不传 timeFrom/timeTo 时服务端默认返回当月数据。type: 2=消费 1=充值 不传=全部。 */
    @GET("/berserker-search/search/personal/turnover")
    suspend fun getTurnover(
        @Query("size") size: Int,
        @Query("current") current: Int,
        @Query("timeFrom") timeFrom: String? = null,
        @Query("timeTo") timeTo: String? = null,
        @Query("type") type: Int? = null,
        @Query("synAccessSource") synAccessSource: String = "h5",
    ): Response<TurnoverResponse>

    /** 收支汇总（金额单位：分）。 */
    @GET("/berserker-search/statistics/turnover/count")
    suspend fun getTurnoverCount(
        @Query("timeFrom") timeFrom: String,
        @Query("timeTo") timeTo: String,
        @Query("synAccessSource") synAccessSource: String = "h5",
    ): Response<TurnoverCountResponse>

    @GET("/charge/feeitem/toAppitem")
    suspend fun enterFeeItem(
        @Query("feeitemid") feeitemid: String,
        @Query("appId") appId: String,
        @Query("synjones-auth") synjonesAuth: String,
        @Query("visitor") visitor: String = "0",
        @Query("type") type: String = "app"
    ): Response<ResponseBody>

    @Headers("Referer: https://ycard.ahu.edu.cn/charge-app/")
    @GET("/charge/feeitem/singleFeeitem")
    suspend fun getSingleFeeItem(
        @Query("feeitemid") feeitemid: String
    ): Response<ResponseBody>

    @POST("/charge/order/thirdOrder")
    suspend fun getOrderThirdData(@Body body: RequestBody): Response<ResponseBody>

    @Headers(
        "Referer: https://ycard.ahu.edu.cn/charge-app/",
        "Origin: https://ycard.ahu.edu.cn"
    )
    @POST("/charge/feeitem/getThirdData")
    suspend fun getFeeItemThirdData(@Body body: RequestBody): Response<ResponseBody>

    @Headers("Referer: https://ycard.ahu.edu.cn/charge-app/")
    @GET("/charge/feeitem/getRechargeRecord")
    suspend fun getElectricityUsageHistory(
        @Query("feeitemid") feeItemId: String,
        @Query("building") building: String,
        @Query("floor") floor: String,
        @Query("room") room: String,
        @Query("campus") campus: String?,
        @Query("startdate") startDate: String,
        @Query("enddate") endDate: String,
        @Query("page") page: Int,
        @Query("row") rows: Int,
        @Query("rtype") recordType: String = "dayEnergy"
    ): Response<ResponseBody>

    @Headers(
        "Referer: https://ycard.ahu.edu.cn/charge-app/",
        "Origin: https://ycard.ahu.edu.cn"
    )
    @POST("/blade-pay/pay")
    suspend fun pay(
        @Body body: RequestBody
    ): Response<ResponseBody>

    @Headers("Referer: https://ycard.ahu.edu.cn/charge-app/")
    @GET("/charge/pay/getpayinfo")
    suspend fun getPayInfo(
        @Query("orderid") orderId: String,
        @Query("userAgent") userAgent: String = "wechat-mp"
    ): Response<ResponseBody>

    @Headers("Referer: https://ycard.ahu.edu.cn/charge-app/")
    @GET("/charge/order/getCurrentTime")
    suspend fun getCurrentTime(): Response<ResponseBody>

//    @GET("/charge/order/personal_data")
//    suspend fun getPersonalData


    @FormUrlEncoded
    @POST("/berserker-auth/oauth/token")
    fun getToken(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("grant_type") grantType: String = "password",
        @Field("scope") scope: String = "all",
        @Field("loginFrom") loginFrom: String = "h5",
        @Field("logintype") loginType: String = "sso",
        @Field("device_token") deviceToken: String = "h5",
        @Field("synAccessSource") synAccessSource: String = "h5",
        @Header("Authorization") authorization: String = "Basic bW9iaWxlX3NlcnZpY2VfcGxhdGZvcm06bW9iaWxlX3NlcnZpY2VfcGxhdGZvcm1fc2VjcmV0",
    ): Call<Token>
    /*
    username=&password=&grant_type=password&scope=all&loginFrom=h5&logintype=sso&device_token=h5&synAccessSource=h5
    * */

    companion object {

        internal const val BASE_URL = "https://ycard.ahu.edu.cn/"
        internal const val LOGIN_TARGET_URL = "https://ycard.ahu.edu.cn/plat/?name=loginTransit"


        private val loggingInterceptor = NetworkLogging.debugInterceptor()

        private val cookieJar = CookieManager.cookieJar


        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val isTokenRequest = path.contains("/oauth/token") || path.contains("/neusoftCas")
            val isTokenInQueryRequest = path == "/charge/feeitem/toAppitem"
            if (isTokenRequest || isTokenInQueryRequest) {
                return@Interceptor chain.proceed(request)
            }

            var token = TokenManager.getToken()

            val newRequest = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) {
                    header("Synjones-Auth", "bearer $token")
                }
            }.build()

            chain.proceed(newRequest)
        }

        private val bathroomMiniProgramInterceptor = Interceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            if (!path.startsWith("/charge/") && !path.startsWith("/blade-pay/")) {
                return@Interceptor chain.proceed(request)
            }
            val builder = request.newBuilder()
                .header("X-Requested-With", "com.tencent.mm")
                .header("User-Agent", BATHROOM_MINI_PROGRAM_USER_AGENT)
            if (path != "/charge/feeitem/toAppitem") {
                builder.header("Authorization", "Basic Y2hhcmdlOmNoYXJnZV9zZWNyZXQ=")
            }
            chain.proceed(builder.build())
        }

        private val bathroomTimingInterceptor = Interceptor { chain ->
            val path = chain.request().url.encodedPath
            val startedAt = System.nanoTime()
            try {
                val response = chain.proceed(chain.request())
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                Log.d(BATHROOM_HTTP_TAG, "$path -> ${response.code} (${elapsedMs}ms)")
                response
            } catch (error: Exception) {
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                Log.w(BATHROOM_HTTP_TAG, "$path failed after ${elapsedMs}ms: ${error.javaClass.simpleName}")
                throw error
            }
        }

        val okHttpClient = AhuHttp.plain(
            connectTimeoutSeconds = 10,
            readTimeoutSeconds = 10,
            writeTimeoutSeconds = 10
        )
            .campusCookies(CookieManager.cookieJar)
            .addInterceptor(interceptor = authInterceptor)
            .apply {
                loggingInterceptor?.let { addInterceptor(it) }
            }
            .build()

        /**
         * The SSO bootstrap must stop as soon as CAS emits a service ticket. Following that
         * redirect all the way into loginTransit can cycle back through neusoftCas before the
         * caller has extracted the one-shot ticket.
         */
        internal val loginRedirectClient = AhuHttp.plain(
            connectTimeoutSeconds = 10,
            readTimeoutSeconds = 10,
            writeTimeoutSeconds = 10,
            followRedirects = false,
            followSslRedirects = false
        )
            .cookieJar(cookieJar)
            .build()

        private fun createApi(client: OkHttpClient): YcardApi =
            retrofit(BASE_URL, client).create(YcardApi::class.java)

        val API = createApi(okHttpClient)

        private val bathroomClient = okHttpClient.newBuilder()
                // toAppitem carries the bearer token in its URL. Do not print that URL in debug logs.
                .apply { loggingInterceptor?.let { interceptors().remove(it) } }
                .addInterceptor(bathroomMiniProgramInterceptor)
                .addInterceptor(bathroomTimingInterceptor)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

        internal val BATHROOM_API = createApi(bathroomClient)

        internal suspend fun enterBathroomFeeItem(
            feeItemId: String,
            appId: String
        ): Response<ResponseBody> {
            val attemptedToken = TokenManager.awaitToken()
                ?: return Response.error(
                    401,
                    "校园卡登录凭证不可用".toResponseBody("text/plain".toMediaType())
                )
            val firstResponse = BATHROOM_API.enterFeeItem(feeItemId, appId, attemptedToken)
            if (firstResponse.code() != 401) return firstResponse

            firstResponse.errorBody()?.close()
            val refreshedToken = TokenManager.refreshAfterUnauthorized(attemptedToken)
                ?: return firstResponse
            return BATHROOM_API.enterFeeItem(feeItemId, appId, refreshedToken)
        }

        internal suspend fun initializeBathroomFeeItem(
            feeItemId: String,
            appId: String
        ): Response<ResponseBody> {
            val entryResponse = enterBathroomFeeItem(feeItemId, appId)
            if (!entryResponse.isSuccessful) return entryResponse
            entryResponse.body()?.close()

            val feeItemResponse = authorizedCall(BATHROOM_API) {
                getSingleFeeItem(feeItemId)
            }
            if (!feeItemResponse.isSuccessful) return feeItemResponse
            feeItemResponse.body()?.close()

            return authorizedCall(BATHROOM_API) {
                getFeeItemThirdData(
                    FormBody.Builder()
                        .add("feeitemid", feeItemId)
                        .add("type", "select")
                        .add("level", "0")
                        .build()
                )
            }
        }

        /**
         * Runs an authenticated campus-card request and retries once when its token has
         * expired. Keeping the refresh at the suspending call site avoids blocking OkHttp's
         * interceptor threads and coalesces concurrent refreshes in [TokenManager].
         */
        suspend fun <T> authorizedCall(
            api: YcardApi = API,
            request: suspend YcardApi.() -> Response<T>
        ): Response<T> {
            val attemptedToken = TokenManager.awaitToken()
            if (attemptedToken.isNullOrBlank()) {
                return Response.error(
                    401,
                    "校园卡登录凭证不可用".toResponseBody("text/plain".toMediaType())
                )
            }
            val firstResponse = api.request()
            if (firstResponse.code() != 401) return firstResponse

            firstResponse.errorBody()?.close()
            if (TokenManager.refreshAfterUnauthorized(attemptedToken).isNullOrBlank()) {
                return firstResponse
            }
            return api.request()
        }

        private const val BATHROOM_MINI_PROGRAM_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/150.0.0.0 Mobile Safari/537.36 " +
                "XWEB/1500117 MMWEBSDK/20260502 MicroMessenger/8.0.76 WeChat/arm64 " +
                "Weixin NetType/WIFI Language/zh_CN ABI/arm64"
        private const val BATHROOM_HTTP_TAG = "BathroomPaymentHttp"

    }
}
