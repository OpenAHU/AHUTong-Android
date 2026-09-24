package com.ahu.ahutong.data.crawler.api.jwxt

import com.ahu.ahutong.data.network.NetworkLogging
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.crawler.model.jwxt.CourseTable
import com.ahu.ahutong.data.crawler.model.jwxt.CurrentTeachWeek
import com.ahu.ahutong.data.crawler.model.jwxt.GetBuildingsResponse
import com.ahu.ahutong.data.crawler.model.jwxt.GetFreeRoomsRequest
import com.ahu.ahutong.data.crawler.model.jwxt.GetFreeRoomsResponse
import com.ahu.ahutong.data.crawler.model.jwxt.GetRoomsResponse
import com.ahu.ahutong.data.crawler.model.jwxt.GradeResponse
import com.ahu.ahutong.data.network.campusAutoLogin
import com.ahu.ahutong.data.network.campusCookies
import com.ahu.ahutong.data.network.campusSessionRefresh
import com.ahu.ahutong.data.network.withoutCampusSessionRefresh
import com.ahu.ahutong.data.session.RepositorySessionExpiryHook
import okhttp3.OkHttpClient
import okhttp3.Authenticator
import okhttp3.ResponseBody
import retrofit2.Response
import com.ahu.ahutong.data.network.retrofit
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import com.ahu.ahutong.data.network.AhuHttp

/**
 * 用指定传输层与 baseUrl 构造接口实例（顶层函数，**不触发伴随对象初始化**，
 * 因此契约测试可以在纯 JVM 环境里使用；生产的 [JwxtApi.API] 仍走同一实现）。
 */
fun createJwxtApi(client: OkHttpClient, baseUrl: String): JwxtApi =
    retrofit(baseUrl, client).create(JwxtApi::class.java)

interface JwxtApi {

    @GET("/student/sso/login")
    suspend fun fetchLoginInfo(): Response<ResponseBody>

    @GET
    suspend fun fetchUrl(@Url url: String): Response<ResponseBody>

    @GET("/student/for-std/course-table/semester/{id}/print-data")
    suspend fun getCourse(
        @Path("id") semesterPathId: Int,
        @Query("semesterId") semesterQueryId: Int,
        @Query("hasExperiment") hasExperiment: Boolean = false
    ): CourseTable

    @GET("/student/for-std/course-table")
    suspend fun fetchCourseTableBasicInfo(): Response<ResponseBody>

    @GET("/student/home/get-current-teach-week")
    suspend fun getCurrentTeachWeek(): CurrentTeachWeek

    @GET("/student/for-std/exam-arrange/info/96223")
    suspend fun getExamInfo(): Response<ResponseBody>

    @GET("/student/for-std/exam-arrange")
    suspend fun fetchExamArrangePage(): Response<ResponseBody>

    // To retrieve a student's examInfo/grade, you need their ID
    // This interface return's student' grade, and it also returns student's ID via its redirect URL
    // So,before you get above data, you need access this interface to get student's ID
    @GET("/student/for-std/grade/sheet")
    suspend fun getGrade(): Response<ResponseBody>

    // GPA rank page (replaces old redirect-based approach)
    @GET("/student/for-std/grade/sheet/semester-index/{id}")
    suspend fun getGpaRankPage(@Path("id") id: String): Response<ResponseBody>

    @GET("/student/for-std/grade/sheet/info/{id}")
    suspend fun getGrade(@Path("id") id: String): GradeResponse

    @FormUrlEncoded
    @POST
    suspend fun device(
        @Url url: String,
        @Field("ul") username: Int,
        @Field("pl") password: Int,
        @Field("rsa") rsa: String,
        @Field("method") method: String = "login"
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST
    suspend fun confirmDeviceForSession(
        @Url url: String,
        @Field("saveDevice") saveDevice: Int = 0,
        @Field("method") method: String = "bind2"
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST
    suspend fun login(
        @Url url: String,
        @Field("rsa") rsa: String,
        @Field("ul") username: Int,
        @Field("pl") password: Int,
        @Field("lt") lt: String,
        @Field("execution") execution: String = "e1s1",
        @Field("_eventId") eventId: String = "submit"
    ): Response<ResponseBody>


    @GET("/student/ws/room/get-buildings")
    suspend fun getBuildings(@Query("campusId") campusId: Int,
                             @Query("hasDataPermission") hasDataPermission: Boolean = false): GetBuildingsResponse

    @GET("/student/ws/room/get-rooms")
    suspend fun getRooms(@Query("buildingId") buildingId: Int,
                         @Query("hasDataPermission") hasDataPermission: Boolean = false,
                         @Query("hasUsableDepartPermission") hasUsableDepartPermission: Boolean = false): GetRoomsResponse

    @POST("/student/ws/room-borrow/free-list")
    suspend fun getFreeRooms(@Body body: GetFreeRoomsRequest): GetFreeRoomsResponse


    companion object {
        private val BASE_URL = "https://jw.ahu.edu.cn/"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        val loggingInterceptor = NetworkLogging.debugInterceptor()

        private val cookieJar = CookieManager.cookieJar

        val okHttpClient = AhuHttp.plain(
            connectTimeoutSeconds = 15,
            readTimeoutSeconds = 30,
            writeTimeoutSeconds = 15
        )
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .header("User-Agent", BROWSER_USER_AGENT)
                        .build()
                )
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


        val API = createJwxtApi(okHttpClient, BASE_URL)

        val LOGIN_API = createJwxtApi(loginOkHttpClient, BASE_URL)
    }
}
