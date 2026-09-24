package com.ahu.ahutong.data.crawler.api.adwmh

import com.ahu.ahutong.data.network.NetworkLogging
import com.ahu.ahutong.data.crawler.model.adwnh.AdwmhApiResponse
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.Balance
import com.ahu.ahutong.data.crawler.model.adwnh.Captcha
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse
import com.ahu.ahutong.data.crawler.model.adwnh.QRcode
import com.ahu.ahutong.data.network.campusAutoLogin
import com.ahu.ahutong.data.network.campusCookies
import com.ahu.ahutong.data.network.campusSessionRefresh
import com.ahu.ahutong.data.network.withoutCampusSessionRefresh
import com.ahu.ahutong.data.session.RepositorySessionExpiryHook
import okhttp3.MultipartBody
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.http.Body
import com.ahu.ahutong.data.network.retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.Url
import com.ahu.ahutong.data.network.AhuHttp

/** 同 [createJwxtApi]：顶层函数，契约测试无需触发伴随对象初始化。 */
fun createAdwmhApi(client: OkHttpClient, baseUrl: String): AdwmhApi =
    retrofit(baseUrl, client).create(AdwmhApi::class.java)

interface AdwmhApi {
    @GET("/remind/authcode")
    suspend fun getAuthCode(): ResponseBody

    @POST("/user/login")
    @FormUrlEncoded
    suspend fun loginWithCaptcha(
        @Field("username") username: String,
        @Field("pwd") password: String,
        @Field("flag") flag: Int,
        @Field("imgcode") imgcode: String
    ): ResponseBody


    @GET("/xzxcard/yue")
    suspend fun getBalance(): Balance


    @GET("/xzxcard/qrcode")
    suspend fun getQrcode(): QRcode

    @GET("/lostfound/campus/all")
    suspend fun getAllcampus(): AllCampus

    @GET("/lostfound/type/all")
    suspend fun getAlllostfoundtype(): AllLostFoundType

    @GET("/lostfound/all")
    suspend fun getLostFoundList(
        @Query("pageNo") pageNo: Int,
        @Query("pageSize") pageSize: Int,
        @Query("state") state: Int
    ): LostFoundResponse//state=1是招领物品，state=2是寻找物品

    @POST("lostfound/saveupdate")
    suspend fun publishLostFound(
        @Body request: LostFoundPublishRequest
    ): AdwmhApiResponse<Any>

    @FormUrlEncoded
    @POST("lostfound/delete")
    suspend fun deleteLostFound(
        @Field("id") id: String
    ): AdwmhApiResponse<Any>

    @POST
    @Multipart
    suspend fun getCaptchaResult(@Url url: String,@Part data: MultipartBody.Part): Captcha



    companion object {
        val loggingInterceptor = NetworkLogging.debugInterceptor()

        private val cookieJar = CookieManager.cookieJar
//        val cookieJar = PersistentCookieJar(
//            SetCookieCache(),
//            SharedPrefsCookiePersistor(MyApp.instance.applicationContext)
//        )

//        private val loginInterceptor =  RedirectLoginInterceptor()

        val BASE_URL = "https://adwmh.ahu.edu.cn/"


        val okHttpClient = AhuHttp.plain()
            .addNetworkInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .build()
                chain.proceed(request)
            }
            .campusAutoLogin()
            .campusSessionRefresh(RepositorySessionExpiryHook())
            .campusCookies(CookieManager.cookieJar)
            .apply {
                loggingInterceptor?.let { addNetworkInterceptor(it) }
            }
            .build()

        private val loginOkHttpClient = okHttpClient.newBuilder()
            .withoutCampusSessionRefresh()
            .build()

        val API = createAdwmhApi(okHttpClient, BASE_URL)

        val LOGIN_API = createAdwmhApi(loginOkHttpClient, BASE_URL)

    }
}
