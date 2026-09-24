package com.ahu.ahutong.data.crawler.api.jwxt

import android.util.Log
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.network.campusAutoLogin
import com.ahu.ahutong.data.network.campusCookies
import com.ahu.ahutong.data.session.RepositorySessionExpiryHook
import com.ahu.ahutong.data.model.EvalAccount
import com.ahu.ahutong.data.model.EvalApiResponse
import com.ahu.ahutong.data.model.EvalCheckParam
import com.ahu.ahutong.data.model.EvalLoginResult
import com.ahu.ahutong.data.model.EvalQuestionnaire
import com.ahu.ahutong.data.model.EvalSearchResult
import com.ahu.ahutong.data.model.EvalSemester
import com.ahu.ahutong.data.model.EvalSubmitRequest
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Response
import com.ahu.ahutong.data.network.retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.ahu.ahutong.data.network.AhuHttp

interface EvaluationApi {

    @GET("/student/for-std/extra-system/student-summation-forstudent/index")
    suspend fun enterEvaluationSystem(): Response<ResponseBody>

    @POST("token/renew")
    suspend fun tokenRenew(@Body body: Map<String, String>): EvalApiResponse<EvalLoginResult>

    @GET("api/v1/poa/sys-auth/account/get-by-login-name")
    suspend fun getAccount(@Query("token") token: String): EvalApiResponse<EvalAccount>

    @GET("api/v1/common/home/current-year")
    suspend fun getCurrentYear(@Query("token") token: String): EvalApiResponse<JsonElement>

    @GET("api/v1/common/home/menu")
    suspend fun getHomeMenu(
        @Query("identity") identity: String
    ): EvalApiResponse<JsonElement>

    @GET("api/v1/common/drop-down/stu_semester")
    suspend fun getSemesters(
        @Query("enabled") enabled: Boolean = true,
        @Query("idc_") idc: String = "self"
    ): EvalApiResponse<List<EvalSemester>>

    @GET("api/v1/common/semester/search")
    suspend fun getSemester(@Query("id") id: String): EvalApiResponse<List<EvalSemester>>

    @GET("api/v1/for-student/student-summation-forstudent/search")
    suspend fun getEvaluationList(
        @Query("queryPage__") page: String = "1,20",
        @Query("orderBy") orderBy: String = "",
        @Query("semesterId") semesterId: String = "",
        @Query("evaluated") evaluated: Boolean = false
    ): EvalApiResponse<EvalSearchResult>

    @GET("api/v1/for-student/student-summation-forstudent/search-check-param/{stdSumTaskId}")
    suspend fun checkParam(@Path("stdSumTaskId") stdSumTaskId: String): EvalApiResponse<EvalCheckParam>

    @GET("api/v1/for-student/student-summation-forstudent/search-questionnaire/{questionnaireId}")
    suspend fun getQuestionnaire(
        @Path("questionnaireId") questionnaireId: String
    ): EvalApiResponse<EvalQuestionnaire>

    @POST("api/v1/for-student/student-summation-forstudent/check-submit")
    suspend fun checkSubmit(@Body request: EvalSubmitRequest): EvalApiResponse<String>

    @POST("api/v1/for-student/student-summation-forstudent/submit")
    suspend fun submit(@Body request: EvalSubmitRequest): EvalApiResponse<Any>

    companion object {
        private const val TAG = "EvaluationApi"
        private const val BASE_URL = "https://jw.ahu.edu.cn/eams5-evaluation-service/"
        private const val EVALUATION_REFERER =
            "https://jw.ahu.edu.cn/evaluation-student-frontend/?bizTypeId=2"
        private const val STUDENT_HOME_REFERER = "https://jw.ahu.edu.cn/student/home"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/150.0.0.0"

        @Volatile
        private var authorizationToken: String = ""

        private val client by lazy { AhuHttp.plain(
            connectTimeoutSeconds = 15,
            readTimeoutSeconds = 30,
            writeTimeoutSeconds = 15
        )
            .campusCookies(
                cookieJar = CookieManager.cookieJar,
                followRedirects = false,
                followSslRedirects = false
            )
            .campusAutoLogin()
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 401 && response.request.url.encodedPath
                        .startsWith("/eams5-evaluation-service/")
                ) {
                    Log.w(
                        TAG,
                        "401 ${redactUrl(response.request.url.toString())}; response body suppressed"
                    )
                }
                response
            }
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept-Language", "zh")
                if (original.url.encodedPath.startsWith("/student/for-std/extra-system/")) {
                    builder
                        .header(
                            "Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                        )
                        .header("Referer", STUDENT_HOME_REFERER)
                } else {
                    builder
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Referer", EVALUATION_REFERER)
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-origin")
                        .header("sec-ch-ua", "\"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"150\", \"Microsoft Edge\";v=\"150\"")
                        .header("sec-ch-ua-mobile", "?0")
                        .header("sec-ch-ua-platform", "\"Windows\"")
                    if (original.method != "GET") {
                        builder.header("Origin", "https://jw.ahu.edu.cn")
                    }
                    val authToken = extractTokenRenewBodyToken(original) ?: authorizationToken
                    authToken.takeIf { it.isNotBlank() }?.let {
                        builder.header("Authorization", it)
                    }
                }
                chain.proceed(builder.build())
            }
            .build()
        }

        val API: EvaluationApi by lazy { retrofit(BASE_URL, client).create(EvaluationApi::class.java) }

        fun setAuthorizationToken(token: String) {
            authorizationToken = token
        }

        private fun extractTokenRenewBodyToken(request: Request): String? {
            if (!request.url.encodedPath.endsWith("/token/renew")) return null
            val body = request.body ?: return null
            return try {
                val buffer = Buffer()
                body.writeTo(buffer)
                JsonParser.parseString(buffer.readUtf8())
                    .asJsonObject
                    .get("token")
                    ?.asString
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Throwable) {
                Log.w(TAG, "failed to parse token renew request body", e)
                null
            }
        }

        private fun redactUrl(url: String): String {
            return url.replace(Regex("token=[^&#]+"), "token=<redacted>")
        }
    }
}
