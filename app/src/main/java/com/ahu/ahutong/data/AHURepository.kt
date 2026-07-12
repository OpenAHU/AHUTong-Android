package com.ahu.ahutong.data

import android.util.Log
import com.ahu.ahutong.AHUApplication
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.auth.AuthRepository
import com.ahu.ahutong.data.base.BaseDataSource
import com.ahu.ahutong.data.crawler.SdkDataSource
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.crawler.manager.TokenManager
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.di.DataEntryPoint
import com.ahu.ahutong.data.exam.ExamRepository
import com.ahu.ahutong.data.grade.GradeRepository
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.mock.MockDataSource
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.schedule.ScheduleRepository
import com.ahu.ahutong.sdk.LocalServiceClient
import com.ahu.ahutong.sdk.RustSDK
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Temporary facade over domain repositories.
 * Prefer injecting domain repositories in new code.
 */
object AHURepository {

    val TAG = this::class.java.simpleName

    private var dataSource: BaseDataSource = SdkDataSource()
    fun initializeDataSource(useMock: Boolean = AHUCache.getMockData()) {
        dataSource = if (useMock) MockDataSource() else SdkDataSource()
    }

    private fun dataEntryPoint(): DataEntryPoint =
        EntryPointAccessors.fromApplication(
            AHUApplication.getApp(),
            DataEntryPoint::class.java,
        )

    private fun scheduleRepository(): ScheduleRepository = dataEntryPoint().scheduleRepository()

    private fun authRepository(): AuthRepository = dataEntryPoint().authRepository()

    private fun gradeRepository(): GradeRepository = dataEntryPoint().gradeRepository()

    private fun examRepository(): ExamRepository = dataEntryPoint().examRepository()

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
     */
    suspend fun getSchedule(isRefresh: Boolean = false): Result<List<Course>> =
        scheduleRepository().getSchedule(isRefresh).toKotlinResult()

    suspend fun getNextSchedule(isRefresh: Boolean = false): Result<List<Course>> =
        scheduleRepository().getNextSchedule(isRefresh).toKotlinResult()

    /**
     * 查询成绩 本地优先
     */
    suspend fun getGrade(isRefresh: Boolean = false): Result<Grade> =
        gradeRepository().getGrade(isRefresh).toKotlinResult()

    /**
     * 获取考试信息
     */
    suspend fun getExamInfo(
        isRefresh: Boolean = false,
        studentID: String,
        studentName: String,
    ): Result<List<com.ahu.ahutong.data.model.Exam>> =
        examRepository()
            .getExamInfo(isRefresh, studentID, studentName)
            .toKotlinResult()

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
     * 登录（native HTTP → JNI → crawler）
     */
    suspend fun loginWithCrawler(username: String, password: String): AHUResponse<User> =
        withContext(Dispatchers.IO) {
            when (val result = authRepository().login(username, password)) {
                is AppResult.Success -> AHUResponse<User>().apply {
                    code = 0
                    data = result.data
                    msg = "登录成功"
                }
                is AppResult.Error -> AHUResponse<User>().apply {
                    code = result.code ?: -1
                    msg = result.message
                }
            }
        }

    suspend fun getBathroomInfo(bathroom: String, tel: String): AHUResponse<BathroomTelInfo> =
        withContext(Dispatchers.IO) {
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
            when (val result = gradeRepository().getGpaRank(studentId)) {
                is AppResult.Success -> AHUResponse<GpaRankInfo>().apply {
                    code = 0
                    data = result.data
                    msg = "success"
                }
                is AppResult.Error -> AHUResponse<GpaRankInfo>().apply {
                    code = result.code ?: -1
                    msg = result.message
                }
            }
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
                Log.w(TAG, "Rust HTTP qrcode failed, fallback to JNI", httpResult.exceptionOrNull())
            }

            val jniResult = RustSDK.getQrcodeSafe()
            if (jniResult.isSuccess) {
                return@withContext jniResult
            }

            Log.w(TAG, "Rust JNI qrcode failed, fallback to Android crawler", jniResult.exceptionOrNull())
            try {
                val response = AdwmhApi.API.getQrcode()
                if (response.code == 10000 && response.`object`.isNotEmpty()) {
                    Result.success(response.`object`)
                } else {
                    Result.failure(Throwable(response.msg))
                }
            } catch (e: Throwable) {
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
            Result.failure(e)
        }
    }
}
