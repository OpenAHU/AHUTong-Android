package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.AHUResponse
import com.ahu.ahutong.data.model.BathroomTelInfo
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before

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

    private fun successfulResponse() = AHUResponse<BathroomTelInfo>().apply {
        code = 0
        data = BathroomTelInfo(msg = "success", code = 200, map = null, message = null)
    }

    private companion object {
        const val PHONE = "13800000000"
    }
}
