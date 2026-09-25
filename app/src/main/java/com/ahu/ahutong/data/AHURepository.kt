package com.ahu.ahutong.data

import android.util.Log
import com.ahu.ahutong.data.base.BaseDataSource
import com.ahu.ahutong.data.crawler.CrawlerDataSource
import com.ahu.ahutong.data.crawler.SdkDataSource
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.configs.Constants
import com.ahu.ahutong.data.crawler.login.WisdomLoginFlow
import com.ahu.ahutong.data.crawler.login.AcademicLoginFlow
import com.ahu.ahutong.data.crawler.login.AcademicPortalLogin
import com.ahu.ahutong.data.crawler.login.AcademicPortalHttp
import com.ahu.ahutong.data.crawler.manager.TokenManager
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.mock.MockDataSource
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.sdk.LocalServiceClient
import com.ahu.ahutong.sdk.RustSDK
import com.ahu.ahutong.utils.DES
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Response
/**
 * @Author: SinkDev
 * @Date: 2021/7/31-下午9:12
 * @Email: 468766131@qq.com
 */
object AHURepository {

    val TAG = this::class.java.simpleName
    const val WEB_VERIFICATION_REQUIRED_CODE = 412

    @Volatile
    private var dataSource: BaseDataSource = SdkDataSource()
    fun initializeDataSource(useMock: Boolean = AHUCache.getMockData()) {
        dataSource = if (useMock) MockDataSource() else SdkDataSource()
    }

    private suspend fun ensureYcardCredential(): Boolean {
        if (AHUCache.getMockData()) return true
        return !TokenManager.awaitToken().isNullOrBlank()
    }

    private fun <T> ycardCredentialNotReadyResponse(): AHUResponse<T> =
        AHUResponse<T>().apply {
            code = -1
            msg = "校园卡登录凭证暂未就绪，请稍后重试"
        }
    
    /**
     * 获取 HTTP 客户端
     */
    private fun getHttpClient(): LocalServiceClient? = LocalServiceClient.getInstance()

    /**
     * 通过semesterId获取课程表
     * @param isRefresh 是否强制刷新
     * @param isRetry 是否为重试（静默重登录后），防止无限循环
     */
    suspend fun getSchedule(isRefresh: Boolean = false): Result<List<Course>> = withContext(Dispatchers.IO) {
        if (!AHUCache.canUseUndergraduateAcademics()) {
            return@withContext Result.failure(IllegalStateException("研究生账号不支持本科课表"))
        }

        if (!isRefresh && !AHUCache.getMockData()) {
            AHUCache.getSchoolTerm()?.let{
                AHUCache.getSchedule(it)?.let{
                    Log.e(TAG, "getSchedule: 本地获取", )
                    return@withContext Result.success(it)
                }
            }
        }

        try {
            val response = dataSource.getSchedule()
            val schedule = response.data
            if (response.isSuccessful && schedule != null) {
                AHUCache.getSchoolTerm()?.let { AHUCache.saveSchedule(it, schedule) }
                Result.success(schedule)

            } else {
                Result.failure(IllegalStateException(response.msg.ifBlank { "课表响应缺少数据" }))
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun getNextSchedule(isRefresh: Boolean = false): Result<List<Course>> = withContext(Dispatchers.IO) {
        if (!AHUCache.canUseUndergraduateAcademics()) {
            return@withContext Result.failure(IllegalStateException("研究生账号不支持本科课表"))
        }
        if (!isRefresh && !AHUCache.getMockData()) {
            AHUCache.getNextSchedule()?.let {
                Log.e(TAG, "getNextSchedule: 本地获取")
                return@withContext Result.success(it)
            }
        }

        try {
            val response = dataSource.getNextSchedule()
            val schedule = response.data
            if (response.isSuccessful && schedule != null) {
                AHUCache.saveNextSchedule(schedule)
                Result.success(schedule)
            } else {
                Result.failure(IllegalStateException(response.msg.ifBlank { "下学期课表响应缺少数据" }))
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    /**
     * 查询成绩 本地优先
     * @param isRefresh Boolean 是否直接获取服务器上的
     * @return Result<List<News>>
     */
    suspend fun getGrade(isRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        if (!AHUCache.canUseUndergraduateAcademics()) {
            return@withContext Result.failure<Grade>(IllegalStateException("研究生账号不支持本科成绩查询"))
        }
        if (!isRefresh && !AHUCache.getMockData()) {
            // 优先从 per-profile 缓存重建合并成绩
            val perProfile = AHUCache.getPerProfileGrades()
            val profileGrades = perProfile.values.filterNotNull()
            if (profileGrades.isNotEmpty()) {
                val allTerms = profileGrades.flatMap { it.termGradeList ?: emptyList<Grade.TermGradeListBean>() }
                val merged = Grade()
                merged.termGradeList = allTerms
                merged.totalGradePointAverage = allTerms.firstOrNull()?.termGradePointAverage ?: "0.0"
                return@withContext Result.success(merged)
            }
            // per-profile 缓存为空 → 走网络获取（同时会自动填充 per-profile 缓存）
        }
        try {
            if (!AHUCache.getMockData()) syncCookies()
            val response = dataSource.getGrade()
            if (response.isSuccessful) {
                Result.success(response.data)
            } else {
                Result.failure(Throwable(response.msg))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getGradeStudentProfiles(): List<GradeStudentProfile> = withContext(Dispatchers.IO) {
        if (!AHUCache.canUseUndergraduateAcademics()) return@withContext emptyList()
        if (AHUCache.getMockData()) {
            Log.i(TAG, "getGradeStudentProfiles skip: mock data")
            return@withContext emptyList()
        }

        AHUCache.getGradeStudentProfiles().takeIf { it.isNotEmpty() }?.let {
            Log.i(TAG, "getGradeStudentProfiles cache size=${it.size}")
            return@withContext it
        }

        syncCookies()
        val resolved = runCatching {
            when (val source = dataSource) {
                is SdkDataSource -> source.getGradeStudentProfiles()
                is CrawlerDataSource -> source.getGradeStudentProfiles()
                else -> emptyList()
            }
        }.onFailure {
            Log.w(TAG, "getGradeStudentProfiles resolve failed", it)
        }.getOrDefault(emptyList())

        if (resolved.isNotEmpty()) {
            Log.i(TAG, "getGradeStudentProfiles resolved size=${resolved.size}")
            return@withContext resolved
        }

        val fallback = AHUCache.getJwxtStudentId()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                GradeStudentProfile(
                    id = it,
                    trainingType = "主修",
                    department = "",
                    major = "本专业"
                )
            }
        if (fallback != null) {
            Log.w(TAG, "getGradeStudentProfiles fallback cached internal id=${fallback.id.maskStudentId()}")
            return@withContext listOf(fallback)
        }

        Log.w(TAG, "getGradeStudentProfiles empty: no internal jwxt student id")
        emptyList()
    }

    /**
     *  获取考试信息
     */
    suspend fun getExamInfo(isRefresh: Boolean = false, studentID: String, studentName: String) =
        withContext(Dispatchers.IO) {
            if (!AHUCache.canUseUndergraduateAcademics()) {
                return@withContext Result.failure<List<com.ahu.ahutong.data.model.Exam>>(
                    IllegalStateException("研究生账号不支持本科考试查询")
                )
            }
            if (!isRefresh && !AHUCache.getMockData()) {
                val localData = AHUCache.getExamInfo().orEmpty()
                if (localData.isNotEmpty()) {
                    return@withContext Result.success(localData)
                }
            }
            try {
                val response = dataSource.getExamInfo(studentID, studentName)
                if (response.isSuccessful) {
                    val exams = response.data ?: emptyList()
                    AHUCache.saveExamInfo(exams)
                    Result.success(exams)
                } else {
                    Result.failure(Throwable(response.msg ?: "获取考试信息失败"))
                }
            } catch (e: Exception) {
                Result.failure(Throwable("请求错误 $e"))
            }
        }

    /**
     *  获取余额
     */
    suspend fun getCardMoney() = withContext(Dispatchers.IO) {
        try {
            val response = dataSource.getCardMoney()
            if (response.isSuccessful) {
                Result.success(response.data)
            } else {
                Result.failure(Throwable(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBathRooms() = withContext(Dispatchers.IO) {
        try {
            val response = dataSource.getBathRooms()
            if (response.isSuccessful) {
                Result.success(response.data)
            } else {
                Result.failure(Throwable(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Authenticate Wisdom AHU first, then positively identify the academic portal.
     * Native login bundles undergraduate authentication, so it cannot identify graduates.
     */
    suspend fun loginWithCrawler(
        username: String,
        password: String
    ): AHUResponse<User> = withContext(Dispatchers.IO) {
        val flow = WisdomLoginFlow(
            fetchCaptcha = { AdwmhApi.LOGIN_API.getAuthCode().use { it.bytes() } },
            solveCaptcha = { bytes ->
                val part = MultipartBody.Part.createFormData(
                    "captcha", "img.jpg", bytes.toRequestBody("image/jpg".toMediaType())
                )
                AhuTong.API.getCaptchaResult(part).result
            },
            submitLogin = { account, secret, captcha ->
                AdwmhApi.LOGIN_API.loginWithCaptcha(account, secret, 0, captcha)
                    .use { it.string() }
            }
        )
        val portal = AcademicPortalLogin(
            diagnostic = { Log.i("AcademicLogin", it) },
            request = AcademicPortalHttp::request
        )
        val response = AcademicLoginFlow(
            wisdom = flow::login,
            undergraduate = { account, secret, user ->
                portal.login(AcademicPortalLogin.UNDERGRADUATE_ENTRY, account, secret, user)
            },
            postgraduate = { account, secret, user ->
                portal.login(AcademicPortalLogin.GMIS_ENTRY, account, secret, user)
            }
        ).login(username, password)
        if (response.isSuccessful) {
            syncAndroidCookiesToRust()
            Log.i(TAG, "Academic login succeeded: ${response.data.academicAccountType}")
        }
        response
    }

    /**
     * Restores the central CAS session for a concrete first-party service. A valid JWXT
     * service cookie does not imply that the CAS TGC is still valid, so campus-card flows
     * must authenticate the exact service URL instead of reloading the JWXT home page.
     */
    suspend fun refreshCentralCasSession(
        username: String,
        password: String,
        casLoginUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!casLoginUrl.startsWith("https://one.ahu.edu.cn/cas/login", ignoreCase = true)) {
            Log.w(TAG, "Rejected non-campus CAS refresh URL")
            return@withContext false
        }

        try {
            val loginPage = JwxtApi.LOGIN_API.fetchUrl(casLoginUrl)
            val pageFinalUrl = loginPage.raw().request.url.toString()
            if (loginPage.code() == WEB_VERIFICATION_REQUIRED_CODE) {
                loginPage.errorBody()?.close()
                return@withContext false
            }
            if (!loginPage.isSuccessful) {
                loginPage.errorBody()?.close()
                return@withContext false
            }

            if (!pageFinalUrl.contains("one.ahu.edu.cn/cas/login", ignoreCase = true)) {
                loginPage.body()?.close()
                return@withContext true
            }

            val loginBody = loginPage.body() ?: return@withContext false
            val document = Jsoup.parse(loginBody.use { it.string() })
            val loginTicket = document.selectFirst("input[name=lt]")?.attr("value")
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext false
            val execution = document.selectFirst("input[name=execution]")?.attr("value")
                ?.takeIf { it.isNotBlank() }
                ?: "e1s1"
            val action = document.selectFirst("form#loginForm")?.attr("action")
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext false
            val loginPostUrl = resolveCasLoginAction(pageFinalUrl, action)
                ?: return@withContext false
            val cipher = DES().strEnc(username + password + loginTicket, "1", "2", "3")

            val deviceResponse = JwxtApi.LOGIN_API.device(
                url = "https://one.ahu.edu.cn/cas/device",
                username = username.length,
                password = password.length,
                rsa = cipher
            )
            val deviceResponseText = deviceResponse.body()?.use { it.string() }.orEmpty()
            deviceResponse.errorBody()?.close()
            val deviceStatus = parseCasDeviceStatus(deviceResponseText)
            val deviceReady = when (deviceStatus) {
                "ok" -> true
                "unbind" -> {
                    val confirmation = JwxtApi.LOGIN_API.confirmDeviceForSession(
                        url = "https://one.ahu.edu.cn/cas/device",
                        saveDevice = 0
                    )
                    val confirmationText = confirmation.body()?.use { it.string() }.orEmpty()
                    confirmation.errorBody()?.close()
                    confirmation.isSuccessful && parseCasDeviceStatus(confirmationText) == "ok"
                }
                else -> false
            }
            if (!deviceResponse.isSuccessful || !deviceReady) {
                Log.w(TAG, "Central CAS device verification was rejected (status=$deviceStatus)")
                return@withContext false
            }

            val loginResponse = JwxtApi.LOGIN_API.login(
                url = loginPostUrl,
                rsa = cipher,
                username = username.length,
                password = password.length,
                lt = loginTicket,
                execution = execution
            )
            val finalUrl = loginResponse.raw().request.url.toString()
            val succeeded = loginResponse.isSuccessful &&
                !finalUrl.contains("one.ahu.edu.cn/cas/login", ignoreCase = true)
            loginResponse.body()?.close()
            loginResponse.errorBody()?.close()
            if (succeeded) syncAndroidCookiesToRust()
            succeeded
        } catch (error: Exception) {
            Log.w(TAG, "Central CAS refresh failed (${error.javaClass.simpleName})")
            false
        }
    }

    private fun parseCasDeviceStatus(responseText: String): String? = runCatching {
        @Suppress("UNCHECKED_CAST")
        (Gson().fromJson(responseText, Map::class.java) as? Map<String, Any?>)
            ?.get("info")
            ?.toString()
    }.getOrNull()

    /**
     * Android's CookieJar retains the effective host for host-only cookies. Exporting from it
     * avoids the ambiguity of inferring domains from cookie names such as JSESSIONID.
     */
    private suspend fun syncAndroidCookiesToRust() {
        val cookiesJson = Gson().toJson(
            com.ahu.ahutong.data.crawler.manager.CookieManager.cookieJar
                .allCookies
                .map { cookie ->
                    mapOf(
                        "name" to cookie.name,
                        "value" to cookie.value,
                        "domain" to cookie.domain,
                        "path" to cookie.path,
                        "secure" to cookie.secure,
                        "http_only" to cookie.httpOnly
                    )
                }
        )
        AHUCache.saveRustCookies(cookiesJson)

        val localServiceImported = getHttpClient()
            ?.init(cookiesJson)
            ?.onFailure { Log.w(TAG, "Failed to sync Android session to local service", it) }
            ?.isSuccess == true
        if (!localServiceImported && RustSDK.isNativeLoaded()) {
            RustSDK.initSafe(cookiesJson)
        }
    }

    suspend fun importWebLoginCookies(cookiesJson: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                require(cookiesJson.isNotBlank() && cookiesJson != "[]") {
                    "教务登录会话为空"
                }

                syncCookiesFromJson(cookiesJson)
                verifyImportedJwxtSession()
                AHUCache.saveRustCookies(cookiesJson)

                getHttpClient()?.init(cookiesJson)?.onFailure {
                    Log.w(TAG, "Failed to import WebView cookies into local service", it)
                }
                if (RustSDK.isNativeLoaded()) {
                    RustSDK.initSafe(cookiesJson)
                }

                Result.success(Unit)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Failed to import WebView login cookies", e)
                Result.failure(e)
            }
        }

    private suspend fun verifyImportedJwxtSession() {
        val response = JwxtApi.API.fetchLoginInfo()
        val finalUrl = response.raw().request.url.toString()
        response.body()?.close()
        response.errorBody()?.close()

        check(response.isSuccessful && finalUrl.endsWith(Constants.JWXT_HOME)) {
            "教务安全验证会话未生效（HTTP ${response.code()}）"
        }
    }

    private fun syncCookies() {
        try {
            // 优先使用 HTTP 客户端
            val httpClient = getHttpClient()
            val json = if (httpClient != null) {
                Log.d("LocalServiceClient", "[syncCookies] Using HTTP client")
                // 使用协程同步获取
                kotlinx.coroutines.runBlocking {
                    httpClient.getCookiesList().getOrDefault("[]")
                }
            } else {
                Log.d("LocalServiceClient", "[syncCookies] Fallback to JNI")
                RustSDK.getCookiesListSafe()
            }
            
            syncCookiesFromJson(json)
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncCookiesFromJson(json: String) {
        val listType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        val cookies: List<Map<String, Any>> = Gson().fromJson(json, listType)

        cookies.forEach {
            val builder = okhttp3.Cookie.Builder()
                .name(it["name"] as String)
                .value(it["value"] as String)
            val domainObj = it["domain"]
            val path = it["path"] as String

            val domain = if (domainObj != null) {
                domainObj as String
            } else {
                if (path.contains("/cas")) "one.ahu.edu.cn" else "jw.ahu.edu.cn"
            }

            builder.domain(domain)
                .path(path)

            if (it["secure"] == true) builder.secure()
            if (it["http_only"] == true) builder.httpOnly()

            val cookie = builder.build()
            com.ahu.ahutong.data.crawler.manager.CookieManager.cookieJar.addCookie(cookie)
        }
        Log.d(TAG, "Cookies synced into Android client: ${cookies.size}")
    }


    suspend fun getBathroomInfo(bathroom: String, tel: String): AHUResponse<BathroomTelInfo> =
        withContext(Dispatchers.IO) {
            if (!ensureYcardCredential()) {
                return@withContext ycardCredentialNotReadyResponse()
            }
            dataSource.getBathroomTelInfo(bathroom = bathroom, tel = tel)
        }


    suspend fun getCardInfo(): AHUResponse<CardInfo> =
        withContext(Dispatchers.IO) {
            if (!ensureYcardCredential()) {
                return@withContext ycardCredentialNotReadyResponse()
            }
            dataSource.getCardInfo()
        }


    suspend fun getOrderThirdData(request: RequestBody): AHUResponse<Response<ResponseBody>> =
        withContext(Dispatchers.IO){
            if (!ensureYcardCredential()) {
                return@withContext ycardCredentialNotReadyResponse()
            }
            dataSource.getOrderThirdData(request)
        }

    suspend fun pay(request: RequestBody):AHUResponse<Response<ResponseBody>> =
        withContext(Dispatchers.IO){
            if (!ensureYcardCredential()) {
                return@withContext ycardCredentialNotReadyResponse()
            }
            dataSource.pay(request)
        }


    suspend fun getSchoolCalendar(): AHUResponse<Response<ResponseBody>> =
        withContext(Dispatchers.IO) {
            dataSource.getSchoolCalendar()
        }

    suspend fun getGpaRankInfo(studentId: String): AHUResponse<GpaRankInfo> =
        withContext(Dispatchers.IO) {
            if (!AHUCache.canUseUndergraduateAcademics()) {
                return@withContext AHUResponse<GpaRankInfo>().apply {
                    code = -1
                    msg = "研究生账号不支持本科绩点排名"
                }
            }
            Log.i(TAG, "getGpaRankInfo start studentId=${studentId.maskStudentId()}")
            syncCookies()
            val response = dataSource.getGpaRankFromHtml(studentId)
            Log.i(
                TAG,
                "getGpaRankInfo finish code=${response.code} hasData=${response.data != null} " +
                    "msg=${response.msg.orEmpty().take(120)}"
            )
            response
        }

    suspend fun getAllCampus(): AHUResponse<AllCampus> =
        withContext(Dispatchers.IO) {
            dataSource.getAllCampus()
        }

    suspend fun getAllLostFoundType(): AHUResponse<AllLostFoundType> =
        withContext(Dispatchers.IO) {
            dataSource.getAllLostFoundType()
        }

    suspend fun getLostFoundList(
        pageNo: Int,
        pageSize: Int,
        state: Int
    ): AHUResponse<LostFoundResponse> =
        withContext(Dispatchers.IO) {

            dataSource.getLostFoundList(
                pageNo,
                pageSize,
                state
            )
        }

    suspend fun publishLostFound(
        request: LostFoundPublishRequest
    ): AHUResponse<Any> =
        withContext(Dispatchers.IO) {
            dataSource.publishLostFound(request)
        }

    suspend fun deleteLostFound(
        id: String
    ): AHUResponse<Any> =
        withContext(Dispatchers.IO) {
            dataSource.deleteLostFound(id)
        }

    suspend fun getQrcode(): Result<String> =
        withContext(Dispatchers.IO) {
            getHttpClient()?.let { httpClient ->
                val httpResult = httpClient.getQrcode()
                if (httpResult.isSuccess) {
                    return@withContext parseQrcodeResponse(httpResult.getOrThrow())
                }
                Log.w(TAG, "Rust HTTP qrcode failed, fallback to JNI (details suppressed)")
            }

            val jniResult = RustSDK.getQrcodeSafe()
            if (jniResult.isSuccess) {
                return@withContext jniResult
            }

            Log.w(TAG, "Rust JNI qrcode failed, fallback to Android crawler (details suppressed)")
            try {
                val response = AdwmhApi.API.getQrcode()
                if (response.code == 10000 && response.`object`.isNotEmpty()) {
                    Result.success(response.`object`)
                } else {
                    Result.failure(Throwable(response.msg))
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Result.failure(e)
            }
        }

    private fun parseQrcodeResponse(json: String): Result<String> {
        return try {
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val code = obj.get("code")?.asInt ?: -1
            val msg = obj.get("msg")?.asString ?: "获取二维码失败"
            val value = obj.get("object")?.asString.orEmpty()
            if (code == 10000 && value.isNotEmpty()) {
                Result.success(value)
            } else {
                Result.failure(Throwable(msg))
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun String.maskStudentId(): String {
        if (length <= 4) return "****"
        return take(2) + "***" + takeLast(2)
    }
}

internal fun resolveCasLoginAction(pageUrl: String, action: String): String? =
    pageUrl.toHttpUrlOrNull()?.resolve(action)?.toString()
