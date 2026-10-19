package com.ahu.ahutong.data.server


import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import com.ahu.ahutong.data.server.model.Captcha
import com.ahu.ahutong.data.server.model.GrayFeatureDecision
import com.ahu.ahutong.data.preview.EarlyAccessMembership
import com.ahu.ahutong.data.preview.EarlyAccessRequest
import com.ahu.ahutong.data.server.model.SchoolCalendarYearsResponse
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.HTTP
import com.ahu.ahutong.data.network.retrofit
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.util.concurrent.TimeUnit
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.network.asciiUserAgent

interface AhuTong {

    @POST("/ocr/captcha")
    @Multipart
    suspend fun getCaptchaResult(@Part data: MultipartBody.Part): Captcha

    @GET("/api/check_apk_update")
    suspend fun getApkUpdateInfo(@Query("subject") subject: String? = null): ApkUpdateInfo

    @GET("/api/early-access")
    suspend fun getEarlyAccess(@Query("subject") subject: String): EarlyAccessMembership

    @POST("/api/early-access")
    suspend fun joinEarlyAccess(@Body request: EarlyAccessRequest): EarlyAccessMembership

    @HTTP(method = "DELETE", path = "/api/early-access", hasBody = true)
    suspend fun leaveEarlyAccess(@Body request: EarlyAccessRequest): EarlyAccessMembership

    @GET("/api/gray/check")
    suspend fun getGrayFeatureDecision(
        @Query("feature") feature: String,
        @Query("subject") subject: String,
        @Query("versionCode") versionCode: Int,
        @Query("versionName") versionName: String
    ): GrayFeatureDecision

    @GET("/api/school_calendars")
    suspend fun getSchoolCalendarYears(): SchoolCalendarYearsResponse

    @Streaming
    @GET("/api/school_calendars/{year}")
    suspend fun getSchoolCalendar(
        @Path("year") year: String
    ): retrofit2.Response<ResponseBody>

    @GET("/download/{filename}")
    suspend fun downloadFile(@Path(value = "filename", encoded = true) filename: String): retrofit2.Response<ResponseBody>


    companion object {
        val BASE_URL = "https://openahu.org"


        val okHttpClient = AhuHttp.plain(
            connectTimeoutSeconds = 20,
            readTimeoutSeconds = 120,
            writeTimeoutSeconds = 30,
            callTimeoutSeconds = 900
        )
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", asciiUserAgent("AHUTong/${BuildConfig.VERSION_NAME} (Android)"))
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(request)
            }
            .build()

        private val grayOkHttpClient = okHttpClient.newBuilder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()

        private fun createApi(client: OkHttpClient) = retrofit(BASE_URL, client).create(AhuTong::class.java)

        val API = createApi(okHttpClient)
        val GRAY_API = createApi(grayOkHttpClient)
        val EARLY_ACCESS_API = createApi(grayOkHttpClient)

    }
}
