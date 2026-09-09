package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.AHUResponse
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.crawler.model.ycard.RequestBody
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.data.model.Data
import com.ahu.ahutong.data.model.MapData
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class BathroomDepositViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `timeout becomes a visible error and the next query can succeed`() = runTest(dispatcher) {
        val expected = successfulResponse()
        var attempts = 0
        val viewModel = BathroomDepositViewModel(
            queryBathroomInfo = { _, _ ->
                if (attempts++ == 0) throw SocketTimeoutException("timed out")
                expected
            },
            queryDispatcher = dispatcher
        )

        viewModel.getBathroomInfo("竹园/龙河", PHONE)
        runCurrent()
        assertEquals("请求超时，请重试", viewModel.queryError.value)
        assertNull(viewModel.info.value)
        assertFalse(viewModel.isQuerying.value)

        viewModel.getBathroomInfo("竹园/龙河", PHONE)
        assertNull(viewModel.queryError.value)
        assertTrue(viewModel.isQuerying.value)
        runCurrent()
        assertSame(expected, viewModel.info.value)
        assertNull(viewModel.queryError.value)
        assertFalse(viewModel.isQuerying.value)
    }

    @Test
    fun `credential failure without data does not expose an invalid account`() = runTest(dispatcher) {
        val viewModel = BathroomDepositViewModel(
            queryBathroomInfo = { _, _ ->
                AHUResponse<BathroomTelInfo>().apply {
                    code = -1
                    msg = "校园卡登录凭证暂未就绪"
                }
            },
            queryDispatcher = dispatcher
        )

        viewModel.getBathroomInfo("竹园/龙河", PHONE)
        runCurrent()

        assertNull(viewModel.info.value)
        assertEquals("校园卡登录凭证暂未就绪", viewModel.queryError.value)
        assertFalse(viewModel.isQuerying.value)
    }

    @Test
    fun `cancelled previous request cannot clear the replacement loading state`() = runTest(dispatcher) {
        val previous = CompletableDeferred<AHUResponse<BathroomTelInfo>>()
        val replacement = CompletableDeferred<AHUResponse<BathroomTelInfo>>()
        val expected = successfulResponse()
        val viewModel = BathroomDepositViewModel(
            queryBathroomInfo = { bathroom, _ ->
                if (bathroom == "竹园/龙河") {
                    // Model an underlying operation that finishes after cancellation.
                    withContext(NonCancellable) { previous.await() }
                } else {
                    replacement.await()
                }
            },
            queryDispatcher = dispatcher
        )

        viewModel.getBathroomInfo("竹园/龙河", PHONE)
        runCurrent()
        viewModel.getBathroomInfo("桔园/蕙园", PHONE)
        runCurrent()
        previous.complete(successfulResponse())
        runCurrent()

        assertTrue(viewModel.isQuerying.value)
        assertNull(viewModel.info.value)
        assertNull(viewModel.queryError.value)

        replacement.complete(expected)
        runCurrent()
        assertSame(expected, viewModel.info.value)
        assertFalse(viewModel.isQuerying.value)
    }

    @Test
    fun `clearing an in flight lookup prevents a late account from reappearing`() = runTest(dispatcher) {
        val response = CompletableDeferred<AHUResponse<BathroomTelInfo>>()
        val viewModel = BathroomDepositViewModel(
            queryBathroomInfo = { _, _ -> withContext(NonCancellable) { response.await() } },
            queryDispatcher = dispatcher
        )

        viewModel.getBathroomInfo("竹园/龙河", PHONE)
        runCurrent()
        viewModel.clearBathroomInfo()
        response.complete(successfulResponse())
        runCurrent()

        assertNull(viewModel.info.value)
        assertNull(viewModel.queryError.value)
        assertFalse(viewModel.isQuerying.value)
    }

    @Test
    fun `coroutine cancellation is not reported as a query failure`() = runTest(dispatcher) {
        val viewModel = BathroomDepositViewModel(
            queryBathroomInfo = { _, _ -> throw CancellationException("query cancelled") },
            queryDispatcher = dispatcher
        )

        viewModel.getBathroomInfo("竹园/龙河", PHONE)
        runCurrent()

        assertNull(viewModel.queryError.value)
        assertNull(viewModel.info.value)
        assertFalse(viewModel.isQuerying.value)
    }

    @Test
    fun `payment obtains a dynamic password map before submitting`() = runTest(dispatcher) {
        val requests = mutableListOf<Map<String, Any>>()
        val viewModel = BathroomDepositViewModel(
            queryBathroomInfo = { _, _ -> successfulAccountResponse() },
            queryDispatcher = dispatcher,
            submitBathroomPayment = { request ->
                requests += request.toMap()
                when (requests.size) {
                    1 -> paymentResponse(
                        """{"code":200,"success":true,"data":{"orderid":"order-1"},"msg":"操作成功"}"""
                    )
                    2 -> paymentResponse(
                        """{"code":200,"msg":"操作成功","payList":[],"order":{}}"""
                    )
                    3 -> paymentResponse(
                        """{"code":200,"success":true,"data":{"passwordMap":{"dynamic-uuid":"7685349012"}},"msg":"操作成功"}"""
                    )
                    4 -> paymentResponse(
                        """{"code":200,"msg":"操作成功","currentTime":1720000000000}"""
                    )
                    else -> paymentResponse(
                        """{"code":400,"success":false,"data":null,"msg":"测试支付失败"}"""
                    )
                }
            },
            paymentDispatcher = dispatcher
        )

        viewModel.getBathroomInfo("竹园/龙河", PHONE)
        runCurrent()
        viewModel.pay("竹园/龙河", "0.1", "012345")
        advanceUntilIdle()

        assertEquals(5, requests.size)
        assertEquals("0", requests[0]["paystep"])
        assertTrue(requests[1].isEmpty())
        assertEquals("2", requests[2]["paystep"])
        assertNull(requests[2]["password"])
        assertTrue(requests[3].isEmpty())
        assertEquals("dynamic-uuid", requests[4]["uuid"])
        assertEquals("789453", requests[4]["password"])
        assertEquals(PayState.Failed("测试支付失败"), viewModel.payState.value)
    }

    private fun successfulResponse() = AHUResponse<BathroomTelInfo>().apply {
        code = 0
        data = BathroomTelInfo(msg = "success", code = 200, map = null, message = null)
    }

    private fun successfulAccountResponse() = AHUResponse<BathroomTelInfo>().apply {
        code = 0
        data = BathroomTelInfo(
            msg = "success",
            code = 200,
            map = MapData(
                showData = null,
                data = Data(
                    projectId = 945,
                    projectName = "安大浴室",
                    accountId = 1,
                    telPhone = PHONE,
                    identifier = null,
                    sex = "未知",
                    name = null,
                    statusId = 0,
                    accountMoney = 100,
                    accountGivenMoney = 0,
                    alias = null,
                    tags = null,
                    isCard = 0,
                    cardStatusId = -1,
                    isUseCode = 1,
                    cardPhysicalId = null,
                    tsmAbstract = "telPhone：$PHONE;竹园/龙河浴室",
                    myCustomInfo = null,
                    message = null
                )
            ),
            message = null
        )
    }

    private fun paymentResponse(json: String) = AHUResponse<Response<ResponseBody>>().apply {
        code = 0
        data = Response.success(json.toResponseBody("application/json".toMediaType()))
        msg = "success"
    }

    private companion object {
        const val PHONE = "13800000000"
    }
}
