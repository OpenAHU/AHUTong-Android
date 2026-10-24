package com.ahu.ahutong.data.crawler

import android.util.Log
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.base.BaseDataSource
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.model.jwxt.GradeResponse
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPaymentRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomCurrentTimeRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPayInfoRequest
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.primaryAccountBalanceCents
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverPage
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverCount
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.crawler.utils.GpaRankHtmlParser
import com.ahu.ahutong.data.model.BathRoom
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.model.Card
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.ScheduleSemester
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.sdk.LocalServiceClient
import com.ahu.ahutong.sdk.RustSDK
import com.google.gson.Gson
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import retrofit2.Response
import com.ahu.ahutong.data.crawler.model.adwnh.Balance
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.server.model.SchoolCalendarYearsResponse
import java.io.File
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.data.crawler.model.adwnh.toAhuResult
import com.ahu.ahutong.data.toAhuError

class SdkDataSource : BaseDataSource {

    val TAG = this::class.java.simpleName
    private val crawlerFallback = CrawlerDataSource()

    /**
     * 获取 HTTP 客户端，如果不可用则返回 null（fallback 到 JNI）
     */
    private fun getHttpClient(): LocalServiceClient? = LocalServiceClient.getInstance()

    override suspend fun getSchedule(
        schoolYear: String,
        schoolTerm: String
    ): AhuResult<List<Course>> {
        return crawlerFallback.getSchedule(schoolYear, schoolTerm)
    }

    override suspend fun getSchedule(): AhuResult<List<Course>> {
        // 优先使用 HTTP 客户端
        val httpClient = getHttpClient()
        if (httpClient != null) {
            Log.d("LocalServiceClient", "[getSchedule] Using HTTP client")
            val result = httpClient.getSchedule()
            if (result.isSuccess) {
                val data = result.getOrNull()
                if (data != null) return AhuResult.Success(data)
            }

            Log.w("LocalServiceClient", "[getSchedule] HTTP failed, fallback to JNI: ${result.exceptionOrNull()?.message}")
        }

        // Fallback: 直接 JNI 调用
        Log.d("LocalServiceClient", "[getSchedule] Fallback to JNI")
        val result = RustSDK.getScheduleSafe()
        if (result.isSuccess) {
            val data = result.getOrNull()
            if (data != null) {
                return AhuResult.Success(data)
            } else {
                Log.w("LocalServiceClient", "[getSchedule] JNI returned null, fallback to Android crawler")
            }
        } else {
            Log.w("LocalServiceClient", "[getSchedule] JNI failed, fallback to Android crawler: ${result.exceptionOrNull()?.message}")
        }

        return crawlerFallback.getSchedule()
    }

    override suspend fun getNextSchedule(): AhuResult<List<Course>> {
        return CrawlerDataSource().getNextSchedule()
    }

    // SDK 暂无真实学期目录和按 ID 课表端口，沿用 Android 教务协议客户端。
    override suspend fun getScheduleSemesters(): AhuResult<List<ScheduleSemester>> =
        crawlerFallback.getScheduleSemesters()

    override suspend fun getSemesterSchedule(semesterId: Int): AhuResult<List<Course>> =
        crawlerFallback.getSemesterSchedule(semesterId)

    override suspend fun getGrade(): AhuResult<Grade> {
        val profiles = resolveGradeProfiles()

        // 优先使用 HTTP 客户端
        val httpClient = getHttpClient()
        if (httpClient != null) {
            if (profiles.size > 1) {
                getHttpGradesForProfiles(httpClient, profiles)?.let { return it }
            }

            Log.d("LocalServiceClient", "[getGrade] Using HTTP client")
            val result = httpClient.getGrade()
            if (result.isSuccess) {
                val data = result.getOrThrow()
                return convertGradeResponse(data).also {
                    cacheSingleProfileGrade(profiles, it.valueOrNull())
                }
            }

            Log.w("LocalServiceClient", "[getGrade] HTTP failed, fallback to JNI: ${result.exceptionOrNull()?.message}")
            if (profiles.size > 1) {
                Log.w("LocalServiceClient", "[getGrade] multi-profile HTTP failed, fallback to Android crawler")
                return crawlerFallback.getGrade()
            }
            val jniResult = RustSDK.getGradeSafe()
            if (jniResult.isSuccess) {
                return convertGradeResponse(jniResult.getOrThrow()).also {
                    cacheSingleProfileGrade(profiles, it.valueOrNull())
                }
            }

            Log.w("LocalServiceClient", "[getGrade] JNI failed, fallback to Android crawler: ${jniResult.exceptionOrNull()?.message}")
            return crawlerFallback.getGrade()
        }

        if (profiles.size > 1) {
            Log.d("LocalServiceClient", "[getGrade] multi-profile without HTTP client, fallback to Android crawler")
            return crawlerFallback.getGrade()
        }

        // Fallback: 直接 JNI 调用
        Log.d("LocalServiceClient", "[getGrade] Fallback to JNI")
        val result = RustSDK.getGradeSafe()
        if (result.isSuccess) {
            return convertGradeResponse(result.getOrThrow()).also {
                cacheSingleProfileGrade(profiles, it.valueOrNull())
            }
        }

        return try {
            crawlerFallback.getGrade()
        } catch (e: Exception) {
            val msg = result.exceptionOrNull()?.message
                ?: "获取成绩失败"
            AhuResult.Failure(
                AhuError.Server(
                    -1,
                    if (msg.contains("error decoding response body", ignoreCase = true)) {
                        "教务系统返回异常（可能登录失效），请重新登录后重试"
                    } else {
                        msg
                    }
                )
            )
        }
    }

    suspend fun getGradeStudentProfiles(): List<GradeStudentProfile> =
        crawlerFallback.getGradeStudentProfiles()

    private suspend fun resolveGradeProfiles(): List<GradeStudentProfile> {
        return runCatching { getGradeStudentProfiles() }
            .onSuccess { Log.i(TAG, "resolveGradeProfiles size=${it.size}") }
            .onFailure { Log.w(TAG, "resolveGradeProfiles failed", it) }
            .getOrDefault(emptyList())
    }

    private suspend fun getHttpGradesForProfiles(
        httpClient: LocalServiceClient,
        profiles: List<GradeStudentProfile>
    ): AhuResult<Grade>? {
        Log.d("LocalServiceClient", "[getGrade] multi-profile HTTP fetch count=${profiles.size}")
        val perProfileGrades = linkedMapOf<GradeStudentProfile, Grade?>()
        profiles.forEach { profile ->
            val result = httpClient.getGrade(profile.id)
            val grade = if (result.isSuccess) {
                convertGradeResponse(result.getOrThrow()).valueOrNull()
            } else {
                Log.w(
                    "LocalServiceClient",
                    "[getGrade] profile fetch failed id=${profile.id.maskStudentId()}: " +
                        result.exceptionOrNull()?.message
                )
                null
            }
            perProfileGrades[profile] = grade
        }

        if (perProfileGrades.values.none { it != null }) {
            Log.w("LocalServiceClient", "[getGrade] multi-profile HTTP returned no grade data")
            return null
        }

        AHUCache.savePerProfileGrades(perProfileGrades)
        return mergeProfileGrades(perProfileGrades.values.filterNotNull())
    }

    private fun cacheSingleProfileGrade(profiles: List<GradeStudentProfile>, grade: Grade?) {
        if (profiles.size != 1 || grade == null) return
        AHUCache.savePerProfileGrades(mapOf(profiles.first() to grade))
    }


    override suspend fun getGpaRankFromHtml(studentId: String): AhuResult<GpaRankInfo> {
        val maskedStudentId = studentId.maskStudentId()
        Log.i(TAG, "getGpaRankFromHtml start studentId=$maskedStudentId")
        try {
            val htmlResponse = JwxtApi.API.getGpaRankPage(studentId)
            Log.i(
                TAG,
                "getGpaRankFromHtml http code=${htmlResponse.code()} " +
                    "success=${htmlResponse.isSuccessful} " +
                    "finalUrl=${htmlResponse.raw().request.url.toString().redactStudentId(studentId)}"
            )
            if (!htmlResponse.isSuccessful || htmlResponse.body() == null) {
                Log.w(TAG, "getGpaRankFromHtml empty/non-success body studentId=$maskedStudentId")
                return AhuResult.Failure(AhuError.Server(-1, "获取成绩排名页面失败"))
            }

            val html = htmlResponse.body()!!.string()
            Log.i(
                TAG,
                "getGpaRankFromHtml html length=${html.length} " +
                    "hasModel=${GpaRankHtmlParser.hasModelAssignment(html)} " +
                    "looksLogin=${html.contains("cas/login", ignoreCase = true) || html.contains("tologin", ignoreCase = true)}"
            )
            val jsObject = GpaRankHtmlParser.extractModelObject(html)
            Log.i(TAG, "getGpaRankFromHtml model extracted length=${jsObject.length}")

            val json = convertJsToJson(jsObject)

            val gpaRankInfo = Gson().fromJson(json, GpaRankInfo::class.java)
            Log.i(
                TAG,
                "getGpaRankFromHtml parsed gpa=${gpaRankInfo.gpa} " +
                    "rank=${gpaRankInfo.majorRank}/${gpaRankInfo.majorHeadCount} " +
                    "semesters=${gpaRankInfo.gpaSemesterSubs.size}"
            )

            return AhuResult.Success(gpaRankInfo)

        } catch (e: Exception) {
            Log.w(TAG, "getGpaRankFromHtml failed studentId=$maskedStudentId", e)
            return AhuResult.Failure(AhuError.ProtocolChanged("解析失败：${e.message}"))
        }
    }

    override suspend fun getAllCampus(): AhuResult<AllCampus> {
        try {
            // 直接请求 JSON 接口
            val campusList = AdwmhApi.API.getAllcampus()

            return AhuResult.Success(campusList)

        } catch (e: Exception) {
            return AhuResult.Failure(e.toAhuError())
        }
    }

    override suspend fun getAllLostFoundType(): AhuResult<AllLostFoundType> {
        try {
            // 直接请求 JSON 接口
            val typeList = AdwmhApi.API.getAlllostfoundtype()
            return AhuResult.Success(typeList)

        } catch (e: Exception) {
            return AhuResult.Failure(e.toAhuError())
        }
    }
    override suspend fun getLostFoundList(
        pageNo: Int,
        pageSize: Int,
        state: Int
    ): AhuResult<LostFoundResponse> {
        try {
            // 直接请求 JSON 接口
            val List = AdwmhApi.API.getLostFoundList(
                pageNo,
                pageSize,
                state
            )
            // 封装返回
            return AhuResult.Success(List)

        } catch (e: Exception) {
            return AhuResult.Failure(e.toAhuError())
        }
    }
    override suspend fun publishLostFound(
        request: LostFoundPublishRequest
    ): AhuResult<Any> {
        return AdwmhApi.API.publishLostFound(request).toAhuResult()
    }
    override suspend fun deleteLostFound(
        id: String
    ): AhuResult<Any> {
        return AdwmhApi.API.deleteLostFound(id).toAhuResult()
    }

    private fun convertJsToJson(js: String): String {
        return js
            .replace(Regex("'"), "\"")                // 单引号 → 双引号
    }

    private fun mergeProfileGrades(grades: List<Grade>): AhuResult<Grade> {
        val termGradeList = grades.flatMap { it.termGradeList ?: emptyList() }
        return AhuResult.Success(GradeMapper.aggregate(termGradeList))
    }

    /** 转换 GradeResponse 为 Grade（聚合算术见 GradeMapper，与爬虫网关共用同一份实现）。 */
    private fun convertGradeResponse(data: GradeResponse): AhuResult<Grade> =
        AhuResult.Success(GradeMapper.fromNativeResponse(data))

    override suspend fun getCardMoney(preferRealtime: Boolean): AhuResult<Card> {
        // Manual refresh uses the same real-time queryCard as the recharge page.
        // Periodic/home fallback deliberately keeps the legacy route to avoid polling it.
        if (preferRealtime) {
            val cents = getCardInfo().valueOrNull()?.primaryAccountBalanceCents()
            if (cents != null) {
                Log.d("LocalServiceClient", "[getCardMoney] Using realtime queryCard")
                return AhuResult.Success(Card().apply { balance = cents / 100.0 })
            }
        }

        getHttpClient()?.let { httpClient ->
            Log.d("LocalServiceClient", "[getCardMoney] Using HTTP client")
            val result = httpClient.getBalance()
            if (result.isSuccess) {
                return AhuResult.Success(result.getOrThrow())
            }
            Log.w("LocalServiceClient", "[getCardMoney] HTTP failed, fallback to JNI: ${result.exceptionOrNull()?.message}")
        }

        val jniResult = RustSDK.getBalanceSafe()
        if (jniResult.isSuccess) {
            return AhuResult.Success(jniResult.getOrThrow())
        }

        Log.w("LocalServiceClient", "[getCardMoney] JNI failed, fallback to Android crawler: ${jniResult.exceptionOrNull()?.message}")
        return crawlerFallback.getCardMoney(preferRealtime = false)
    }

    override suspend fun getBathRooms(): AhuResult<List<BathRoom>> {
        return crawlerFallback.getBathRooms()
    }

    override suspend fun getExamInfo(studentID: String, studentName: String): AhuResult<List<Exam>> {
        // The Android crawler understands the current server-rendered exam page. The bundled
        // service can still expose the legacy payload shape and currently spends several seconds
        // before reporting that it cannot parse it, so use it only as a recovery path.
        val crawlerResult = crawlerFallback.getExamInfo(studentID, studentName)
        if (crawlerResult.isSuccess) {
            return crawlerResult
        }

        Log.w(
            "LocalServiceClient",
            "[getExamInfo] Android crawler failed, trying local service: " +
                crawlerResult.errorOrNull()?.toUserMessage().orEmpty()
        )
        try {
            val httpClient = getHttpClient()
            val result = if (httpClient != null) {
                Log.d("LocalServiceClient", "[getExamInfo] Using HTTP client")
                httpClient.getExamInfo()
            } else {
                Log.d("LocalServiceClient", "[getExamInfo] Fallback to JNI")
                RustSDK.getExamInfoSafe()
            }
            val recovered = result.getOrNull()
            if (result.isSuccess && recovered != null) {
                return AhuResult.Success(recovered)
            }
            Log.w(
                "LocalServiceClient",
                "[getExamInfo] Local service recovery failed: ${result.exceptionOrNull()?.message}"
            )
            return crawlerResult
        } catch (e: Exception) {
            Log.w("LocalServiceClient", "[getExamInfo] Local service recovery threw", e)
            return crawlerResult
        }
    }

    override suspend fun getBathroomTelInfo(
        bathroom: String,
        tel: String
    ): AhuResult<BathroomTelInfo> {
        val (feeitemid, appId) = when (bathroom) {
            "竹园/龙河" -> "409" to "55"
            "桔园/蕙园" -> "430" to "56"

            else -> return AhuResult.Failure(AhuError.Server(-1, "目前没有这个浴室啊"))
        }

        val initialization = YcardApi.initializeBathroomFeeItem(feeitemid, appId)
        if (!initialization.isSuccessful) {
            initialization.errorBody()?.close()
            return AhuResult.Failure(
                AhuError.Server(initialization.code(), "浴室缴费会话初始化失败")
            )
        }
        initialization.body()?.close()


        val formBody = FormBody.Builder()
            .add("feeitemid", feeitemid)
            .add("type", "IEC")
            .add("level", "1")
            .add("telPhone", tel)
            .build()


        val res = YcardApi.authorizedCall(YcardApi.BATHROOM_API) {
            getFeeItemThirdData(formBody)
        }

        if (res.isSuccessful) {
            val responseBody = res.body()
            val responseJson = responseBody?.string()

            val bathroomInfo = Gson().fromJson(responseJson, BathroomTelInfo::class.java)

            bathroomInfo?.let {
                return AhuResult.Success(it)
            }
            return AhuResult.Failure(AhuError.Server(-1, "数据返回错误"))
        }

        return res.toClosedFailure("请求接口失败")
    }

    override suspend fun getCardInfo(): AhuResult<CardInfo> {
        val result = YcardApi.authorizedCall { loadCardRecharge() }
        val body = result.body()
        return if (result.isSuccessful && body != null) {
            AhuResult.Success(body)
        } else {
            result.toClosedFailure("校园卡信息加载失败：${result.message()}")
        }
    }


    override suspend fun getBillPage(
        page: Int,
        size: Int,
        timeFrom: String?,
        timeTo: String?,
        type: Int?
    ): AhuResult<TurnoverPage> {
        val result = YcardApi.authorizedCall {
            getTurnover(size = size, current = page, timeFrom = timeFrom, timeTo = timeTo, type = type)
        }
        val body = result.body()?.data
        return if (result.isSuccessful && body != null) {
            AhuResult.Success(body)
        } else {
            result.toClosedFailure("账单加载失败：${result.message()}")
        }
    }

    override suspend fun getBillSummary(timeFrom: String, timeTo: String): AhuResult<TurnoverCount> {
        val result = YcardApi.authorizedCall { getTurnoverCount(timeFrom = timeFrom, timeTo = timeTo) }
        val body = result.body()?.data
        return if (result.isSuccessful && body != null) {
            AhuResult.Success(body)
        } else {
            result.toClosedFailure("账单汇总加载失败：${result.message()}")
        }
    }

    override suspend fun getOrderThirdData(request : RequestBody): AhuResult<Response<ResponseBody>> {
        val call = YcardApi.authorizedCall { getOrderThirdData(request.toFormBody()) }
        return if (call.isSuccessful) {
            AhuResult.Success(call)
        } else {
            call.toClosedFailure()
        }
    }

    override suspend fun pay(request: RequestBody): AhuResult<Response<ResponseBody>> {
        val call = when (request) {
            is BathroomPayInfoRequest -> YcardApi.authorizedCall(YcardApi.BATHROOM_API) {
                getPayInfo(request.orderId)
            }
            is BathroomCurrentTimeRequest -> YcardApi.authorizedCall(YcardApi.BATHROOM_API) {
                getCurrentTime()
            }
            is BathroomPaymentRequest -> YcardApi.authorizedCall(YcardApi.BATHROOM_API) {
                pay(request.toFormBody())
            }
            else -> YcardApi.authorizedCall { pay(request.toFormBody()) }
        }
        return if (call.isSuccessful) {
            AhuResult.Success(call)
        } else {
            call.toClosedFailure()
        }
    }

    override suspend fun getSchoolCalendar(): AhuResult<Response<ResponseBody>> {
        if (RustSDK.isNativeLoaded()) {
            try {
                val context = AppEnvironmentHolder.context()
                val dir = File(context.filesDir, "images")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "xiaoli-rust-download.jpg")
                if (file.exists()) file.delete()

                val ok = RustSDK.downloadSchoolCalendar(file.absolutePath)
                if (ok && file.exists() && file.length() > 0L) {
                    return AhuResult.Success(
                        Response.success(
                            file.inputStream().source().buffer()
                                .asResponseBody("image/jpeg".toMediaType(), file.length())
                        )
                    )
                }
                Log.w(TAG, "downloadSchoolCalendar returned false or empty file, fallback to Android API")
            } catch (e: Throwable) {
                Log.w(TAG, "downloadSchoolCalendar native failed, fallback to Android API", e)
            }
        }

        return crawlerFallback.getSchoolCalendar()
    }

    override suspend fun getSchoolCalendarYears(): AhuResult<SchoolCalendarYearsResponse> =
        crawlerFallback.getSchoolCalendarYears()

    override suspend fun getSchoolCalendar(year: String): AhuResult<Response<ResponseBody>> =
        crawlerFallback.getSchoolCalendar(year)


    suspend fun getStudentId(): String {
        val profiles = crawlerFallback.getGradeStudentProfiles()
        if (profiles.isNotEmpty()) return profiles.first().id
        val lastURL = JwxtApi.API.getGrade().raw().request.url.toString()
        val data = lastURL.split("/")
        return data.last()
    }

    private fun String.maskStudentId(): String {
        if (length <= 4) return "****"
        return take(2) + "***" + takeLast(2)
    }

    private fun String.redactStudentId(studentId: String): String {
        if (studentId.isBlank()) return this
        return replace(studentId, studentId.maskStudentId())
    }
}
