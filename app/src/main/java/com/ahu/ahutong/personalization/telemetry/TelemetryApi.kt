package com.ahu.ahutong.personalization.telemetry

import com.ahu.ahutong.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import com.ahu.ahutong.data.network.retrofit
import retrofit2.http.Header
import retrofit2.http.POST
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.network.asciiUserAgent

interface TelemetryApi {
    @POST("/v1/on-device-model-evaluations/credentials")
    suspend fun credential(
        @Body request: TelemetryCredentialRequest
    ): retrofit2.Response<TelemetryCredentialResponse>

    @POST("/v1/on-device-model-evaluations/batch")
    suspend fun upload(
        @Header("Authorization") telemetryCredential: String,
        @Header("X-Body-SHA256") bodySha256Hex: String,
        @Body exactBody: RequestBody
    ): retrofit2.Response<ResponseBody>

    @POST("/v1/on-device-model-evaluations/delete")
    suspend fun delete(
        @Body request: TelemetryDeletionRequest
    ): retrofit2.Response<ResponseBody>

    companion object {
        private const val BASE_URL = "https://openahu.org"
        private val allowedPaths = setOf(
            "/v1/on-device-model-evaluations/credentials",
            "/v1/on-device-model-evaluations/batch",
            "/v1/on-device-model-evaluations/delete"
        )

        private val client = AhuHttp.plain(
            connectTimeoutSeconds = 10,
            readTimeoutSeconds = 20,
            writeTimeoutSeconds = 20,
            callTimeoutSeconds = 30,
            followRedirects = false,
            followSslRedirects = false
        )
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor { chain ->
                val request = chain.request()
                check(request.url.isHttps && request.url.host == "openahu.org" && request.url.encodedPath in allowedPaths) {
                    "telemetry request escaped the fixed HTTPS allowlist"
                }
                chain.proceed(
                    request.newBuilder()
                        .removeHeader("Cookie")
                        .header("User-Agent", asciiUserAgent("AHUTong/${BuildConfig.VERSION_NAME} (Android telemetry)"))
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()

        val API: TelemetryApi = retrofit(BASE_URL, client).create(TelemetryApi::class.java)
    }
}
