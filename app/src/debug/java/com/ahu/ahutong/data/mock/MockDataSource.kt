package com.ahu.ahutong.data.mock

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.base.BaseDataSource
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundItem
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPage
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse
import com.ahu.ahutong.data.crawler.model.jwxt.FreeRoom
import com.ahu.ahutong.data.crawler.model.jwxt.GetBuildingsResponseItem
import com.ahu.ahutong.data.crawler.model.ycard.CardInfo
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverPage
import com.ahu.ahutong.data.crawler.model.ycard.TurnoverCount
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.model.BathRoom
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.model.Card
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.schedule.ScheduleSemester
import com.ahu.ahutong.data.server.model.SchoolCalendarYearsResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.util.Base64
import kotlin.math.ceil

class MockDataSource : BaseDataSource {
    private val gson = Gson()

    override suspend fun getSchedule(
        schoolYear: String,
        schoolTerm: String
    ): AhuResult<List<Course>> =
        scenarioResponse(
            endpoint = MockEditableEndpoint.CurrentSchedule,
            type = object : TypeToken<List<Course>>() {}.type
        ) { it.academic.currentSchedule }

    override suspend fun getSchedule(): AhuResult<List<Course>> =
        scenarioResponse(
            endpoint = MockEditableEndpoint.CurrentSchedule,
            type = object : TypeToken<List<Course>>() {}.type
        ) { it.academic.currentSchedule }

    override suspend fun getNextSchedule(): AhuResult<List<Course>> =
        scenarioResponse(
            endpoint = MockEditableEndpoint.NextSchedule,
            type = object : TypeToken<List<Course>>() {}.type
        ) { it.academic.nextSchedule }

    override suspend fun getScheduleSemesters(): AhuResult<List<ScheduleSemester>> =
        scenarioResponse { MockSemesterFixtures.semesters }

    override suspend fun getSemesterSchedule(semesterId: Int): AhuResult<List<Course>> {
        return when (semesterId) {
            MockSemesterFixtures.currentId -> getSchedule()
            in MockSemesterFixtures.historyIds -> scenarioResponse { scenario ->
                if (scenario.academic.currentSchedule.isEmpty() && scenario.academic.nextSchedule.isEmpty()) {
                    emptyList()
                } else {
                    MockSemesterFixtures.courses(semesterId)
                }
            }
            else -> failure("Mock 中没有该学期：$semesterId")
        }
    }

    override suspend fun getGrade(): AhuResult<Grade> =
        scenarioResponse(MockEditableEndpoint.Grade, Grade::class.java) { it.academic.grade }

    override suspend fun getCardMoney(preferRealtime: Boolean): AhuResult<Card> =
        scenarioResponse(MockEditableEndpoint.CardMoney, Card::class.java) { it.payment.cardMoney }

    override suspend fun getBathRooms(): AhuResult<List<BathRoom>> =
        scenarioResponse(
            endpoint = MockEditableEndpoint.Bathrooms,
            type = object : TypeToken<List<BathRoom>>() {}.type
        ) { it.campus.bathrooms }

    override suspend fun getExamInfo(
        studentID: String,
        studentName: String
    ): AhuResult<List<Exam>> =
        scenarioResponse(
            endpoint = MockEditableEndpoint.Exams,
            type = object : TypeToken<List<Exam>>() {}.type
        ) { it.academic.exams }

    override suspend fun getBathroomTelInfo(
        bathroom: String,
        tel: String
    ): AhuResult<BathroomTelInfo> {
        val scenario = MockScenarioController.activeScenario()
        activeBehavior(scenario).failureOrNull<BathroomTelInfo>()?.let { return it }
        val accounts = overrideValue<Map<String, BathroomTelInfo>>(
            endpoint = MockEditableEndpoint.BathroomAccounts,
            type = object : TypeToken<Map<String, BathroomTelInfo>>() {}.type
        ) ?: scenario.campus.bathroomAccounts
        val account = accounts[bathroom]
            ?: return failure("Mock 暂不支持该浴室：$bathroom")
        val map = account.map ?: return success(account)
        val data = map.data ?: return success(account)
        return success(
            account.copy(
                map = map.copy(
                    showData = map.showData?.copy(phone = tel),
                    data = data.copy(
                        telPhone = tel,
                        identifier = "mock-$tel-${data.projectId}"
                    )
                )
            )
        )
    }

    override suspend fun getCardInfo(): AhuResult<CardInfo> =
        scenarioResponse(MockEditableEndpoint.CardInfo, CardInfo::class.java) { it.payment.cardInfo }

    override suspend fun getBillPage(
        page: Int,
        size: Int,
        timeFrom: String?,
        timeTo: String?,
        type: Int?
    ): AhuResult<TurnoverPage> = AhuResult.Success(
        TurnoverPage(
            records = if (page > 1) emptyList() else listOf(
                TurnoverRecord(
                    orderId = "mock-1",
                    tranamt = 560,
                    cardBalance = 32150,
                    typeFrom = "2",
                    turnoverType = "二维码支付",
                    consumeTypeName = "联机扫码支付(被扫)",
                    resume = "北二区食堂一楼-扫码支付",
                    toMerchant = "北二区食堂一楼",
                    effectdateStr = "2026-09-22 12:05:30"
                ),
                TurnoverRecord(
                    orderId = "mock-2",
                    tranamt = 10000,
                    cardBalance = 32710,
                    typeFrom = "1",
                    turnoverType = "充值",
                    consumeTypeName = null,
                    resume = "校园卡充值",
                    toMerchant = "慧新e校",
                    effectdateStr = "2026-09-21 20:11:02"
                )
            ),
            total = 2,
            pages = 1
        )
    )

    override suspend fun getBillSummary(timeFrom: String, timeTo: String): AhuResult<TurnoverCount> =
        AhuResult.Success(TurnoverCount(expenses = 560, income = 10000))

    override suspend fun getOrderThirdData(
        request: RequestBody
    ): AhuResult<Response<ResponseBody>> {
        val scenario = MockScenarioController.activeScenario()
        val behavior = activeBehavior(scenario)
        behavior.failureOrNull<Response<ResponseBody>>()?.let { return it }
        if (behavior.paymentMode == MockPaymentMode.GatewayError) {
            return success(
                mockResponse(
                    """{"code":500,"success":false,"data":"","msg":"${scenario.payment.failureMessage}"}""",
                    "MOCK-GATEWAY-ORDER"
                )
            )
        }
        val orderId = "${scenario.payment.cardOrderPrefix}-${System.currentTimeMillis()}"
        return success(
            mockResponse(
                """{"code":200,"success":true,"data":{"orderid":"$orderId"},"msg":"success"}""",
                orderId
            )
        )
    }

    override suspend fun pay(
        request: RequestBody
    ): AhuResult<Response<ResponseBody>> {
        val scenario = MockScenarioController.activeScenario()
        val behavior = activeBehavior(scenario)
        behavior.failureOrNull<Response<ResponseBody>>()?.let { return it }
        val orderId = request.getParam("orderid")?.toString()
            ?: "${scenario.payment.bathroomOrderPrefix}-${System.currentTimeMillis()}"
        val isFinalPay = request.getParam("paystep")?.toString() == "2"
        val body = when {
            isFinalPay && behavior.paymentMode != MockPaymentMode.Success ->
                """{"code":500,"success":false,"data":"","msg":"${scenario.payment.failureMessage}"}"""

            isFinalPay ->
                """{"code":200,"success":true,"data":"$orderId","msg":"${scenario.payment.successMessage}"}"""

            else ->
                """{"code":200,"success":true,"data":{"orderid":"$orderId"},"msg":"success"}"""
        }
        return success(mockResponse(body, orderId))
    }

    override suspend fun getGpaRankFromHtml(studentId: String): AhuResult<GpaRankInfo> =
        scenarioResponse(MockEditableEndpoint.GpaRank, GpaRankInfo::class.java) { it.academic.gpaRankInfo }

    override suspend fun getAllCampus(): AhuResult<AllCampus> =
        scenarioResponse(MockEditableEndpoint.LostFoundCampuses, AllCampus::class.java) {
            it.discovery.lostFoundCampuses
        }

    override suspend fun getAllLostFoundType(): AhuResult<AllLostFoundType> =
        scenarioResponse(MockEditableEndpoint.LostFoundTypes, AllLostFoundType::class.java) {
            it.discovery.lostFoundTypes
        }

    override suspend fun getLostFoundList(
        pageNo: Int,
        pageSize: Int,
        state: Int
    ): AhuResult<LostFoundResponse> {
        val scenario = MockScenarioController.activeScenario()
        activeBehavior(scenario).failureOrNull<LostFoundResponse>()?.let { return it }
        val items = overrideValue<List<LostFoundItem>>(
            endpoint = MockEditableEndpoint.LostFoundItems,
            type = object : TypeToken<List<LostFoundItem>>() {}.type
        ) ?: scenario.discovery.lostFoundItems
        val allItems = items.filter { it.state == state }
        val from = ((pageNo - 1) * pageSize).coerceAtLeast(0)
        val pageItems = allItems.drop(from).take(pageSize)
        val totalPages = ceil(allItems.size.toDouble() / pageSize.toDouble()).toInt().coerceAtLeast(1)
        return success(
            LostFoundResponse(
                code = 0,
                msg = "success",
                data = LostFoundPage(
                    pageNum = pageNo,
                    pageSize = pageSize,
                    size = pageItems.size,
                    startRow = if (pageItems.isEmpty()) 0 else from + 1,
                    endRow = from + pageItems.size,
                    total = allItems.size,
                    pages = totalPages,
                    list = pageItems
                )
            )
        )
    }

    override suspend fun getSchoolCalendar(): AhuResult<Response<ResponseBody>> =
        scenarioResponse { scenario ->
            Response.success(
                Base64.getDecoder()
                    .decode(MockOverrideStore.get(MockEditableEndpoint.SchoolCalendar) ?: scenario.campus.calendarJpegBase64)
                    .toResponseBody("image/jpeg".toMediaType())
            )
        }

    override suspend fun getSchoolCalendarYears(): AhuResult<SchoolCalendarYearsResponse> =
        scenarioResponse {
            SchoolCalendarYearsResponse(
                years = listOf("2026-2027", "2025-2026", "2024-2025"),
                latestYear = "2026-2027"
            )
        }

    override suspend fun getSchoolCalendar(year: String): AhuResult<Response<ResponseBody>> =
        getSchoolCalendar()

    override suspend fun publishLostFound(
        request: LostFoundPublishRequest
    ): AhuResult<Any> =
        scenarioResponse { Any() }

    override suspend fun deleteLostFound(
        id: String
    ): AhuResult<Any> =
        scenarioResponse { Any() }

    private fun mockResponse(json: String, orderId: String): Response<ResponseBody> {
        val raw = okhttp3.Response.Builder()
            .request(
                Request.Builder()
                    .url("https://mock.ahu.edu.cn/payment/result?orderid=$orderId")
                    .build()
            )
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        return Response.success(json.toResponseBody("application/json".toMediaType()), raw)
    }

    private fun <T> scenarioResponse(
        endpoint: MockEditableEndpoint,
        type: java.lang.reflect.Type,
        block: (MockScenario) -> T
    ): AhuResult<T> {
        val scenario = MockScenarioController.activeScenario()
        activeBehavior(scenario).failureOrNull<T>()?.let { return it }
        return success(overrideValue(endpoint, type) ?: block(scenario))
    }

    private fun <T> scenarioResponse(block: (MockScenario) -> T): AhuResult<T> {
        val scenario = MockScenarioController.activeScenario()
        activeBehavior(scenario).failureOrNull<T>()?.let { return it }
        return success(block(scenario))
    }

    private fun activeBehavior(scenario: MockScenario): MockBehavior =
        overrideValue(
            endpoint = MockEditableEndpoint.Behavior,
            type = MockBehavior::class.java
        ) ?: scenario.behavior

    private fun <T> overrideValue(
        endpoint: MockEditableEndpoint,
        type: java.lang.reflect.Type
    ): T? =
        MockOverrideStore.get(endpoint)?.let { raw ->
            runCatching { gson.fromJson<T>(raw, type) }.getOrNull()
        }

    private fun <T> overrideValue(
        endpoint: MockEditableEndpoint,
        type: Class<T>
    ): T? =
        MockOverrideStore.get(endpoint)?.let { raw ->
            runCatching { gson.fromJson(raw, type) }.getOrNull()
        }

    private fun <T> MockBehavior.failureOrNull(): AhuResult<T>? =
        if (shouldFail) failure(errorMessage) else null

    /** msg 参数保留以兼容既有调用点；统一错误模型下成功结果不再携带文案。 */
    @Suppress("UNUSED_PARAMETER")
    private fun <T> success(data: T, msg: String = "success"): AhuResult<T> = AhuResult.Success(data)

    private fun <T> failure(msg: String): AhuResult<T> =
        AhuResult.Failure(AhuError.Server(-1, msg))
}

object MockCampusData {
    private val gson = Gson()

    fun buildings(campusId: Int): List<GetBuildingsResponseItem> {
        val override = MockOverrideStore.get(MockEditableEndpoint.ClassroomBuildings)?.let { raw ->
            runCatching {
                gson.fromJson<Map<Int, List<GetBuildingsResponseItem>>>(
                    raw,
                    object : TypeToken<Map<Int, List<GetBuildingsResponseItem>>>() {}.type
                )
            }.getOrNull()
        }
        return (override ?: MockScenarioController.activeScenario().campus.classroomBuildings)[campusId].orEmpty()
    }

    fun freeRooms(
        campusId: Int,
        buildingIds: List<Int>
    ): List<FreeRoom> {
        val override = MockOverrideStore.get(MockEditableEndpoint.ClassroomRooms)?.let { raw ->
            runCatching {
                gson.fromJson<Map<Int, List<FreeRoom>>>(
                    raw,
                    object : TypeToken<Map<Int, List<FreeRoom>>>() {}.type
                )
            }.getOrNull()
        }
        return (override ?: MockScenarioController.activeScenario().campus.classroomRooms)[campusId]
            .orEmpty()
            .filter { buildingIds.isEmpty() || it.building.id in buildingIds }
    }
}
