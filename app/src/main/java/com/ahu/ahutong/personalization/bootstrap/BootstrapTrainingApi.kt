package com.ahu.ahutong.personalization.bootstrap

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

interface BootstrapTrainingApi {
    @POST("/v1/bootstrap-training-data/credentials")
    suspend fun credential(
        @Body request: BootstrapTrainingCredentialRequest
    ): retrofit2.Response<BootstrapTrainingCredentialResponse>

    @POST("/v1/bootstrap-training-data/batches")
    suspend fun upload(
        @Header("Authorization") credential: String,
        @Header("X-Body-SHA256") bodySha256Hex: String,
        @Body exactBody: RequestBody
    ): retrofit2.Response<ResponseBody>

    @POST("/v1/bootstrap-training-data/delete")
    suspend fun delete(
        @Body request: BootstrapTrainingDeletionRequest
    ): retrofit2.Response<ResponseBody>

    companion object {
        private const val BASE_URL = "https://openahu.org"
        private val allowedPaths = setOf(
            "/v1/bootstrap-training-data/credentials",
            "/v1/bootstrap-training-data/batches",
            "/v1/bootstrap-training-data/delete"
        )

        private val client = AhuHttp.plain(
            connectTimeoutSeconds = 10,
            readTimeoutSeconds = 30,
            writeTimeoutSeconds = 30,
            callTimeoutSeconds = 45,
            followRedirects = false,
            followSslRedirects = false
        )
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor { chain ->
                val request = chain.request()
                check(request.url.isHttps && request.url.host == "openahu.org" && request.url.encodedPath in allowedPaths) {
                    "bootstrap training request escaped the fixed HTTPS allowlist"
                }
                chain.proceed(
                    request.newBuilder()
                        .removeHeader("Cookie")
                        .removeHeader("Authorization")
                        .apply {
                            request.header("Authorization")?.let { header("Authorization", it) }
                        }
                        .header("User-Agent", asciiUserAgent("AHUTong/${BuildConfig.VERSION_NAME} (Android bootstrap-training)"))
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()

        val API: BootstrapTrainingApi = retrofit(BASE_URL, client).create(BootstrapTrainingApi::class.java)
    }
}
