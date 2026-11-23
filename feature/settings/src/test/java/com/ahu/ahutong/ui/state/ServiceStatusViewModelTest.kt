package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.core.designsystem.RefreshState
import com.ahu.ahutong.data.status.ServiceHealth
import com.ahu.ahutong.data.status.ServiceStatusGateway
import com.ahu.ahutong.data.status.ServiceStatusGroup
import com.ahu.ahutong.data.status.ServiceStatusMonitor
import com.ahu.ahutong.data.status.ServiceStatusSnapshot
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceStatusViewModelTest {

    private class FakeServiceStatusGateway(
        var result: AhuResult<ServiceStatusSnapshot>
    ) : ServiceStatusGateway {
        var callCount: Int = 0

        override suspend fun fetchStatus(): AhuResult<ServiceStatusSnapshot> {
            callCount++
            return result
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load error transitions to Failed and retry succeeds`() = runTest {
        val gateway = FakeServiceStatusGateway(AhuResult.Failure(AhuError.Network))
        val vm = ServiceStatusViewModel(gateway)

        assertEquals(ServiceStatusViewModel.UiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        val failed = assertIs<ServiceStatusViewModel.UiState.Failed>(vm.uiState.value)
        assertEquals(AhuError.Network.toUserMessage(), failed.message)

        gateway.result = AhuResult.Success(sampleSnapshot())
        vm.retry()
        advanceUntilIdle()

        val ready = assertIs<ServiceStatusViewModel.UiState.Ready>(vm.uiState.value)
        assertEquals(1, ready.snapshot.upCount)
        assertEquals(RefreshState.IDLE, ready.refreshState)
        assertNull(ready.refreshError)
    }

    @Test
    fun `refresh failure preserves stale snapshot and reports error banner`() = runTest {
        val gateway = FakeServiceStatusGateway(AhuResult.Success(sampleSnapshot()))
        val vm = ServiceStatusViewModel(gateway)
        advanceUntilIdle()

        gateway.result = AhuResult.Failure(AhuError.Timeout)
        vm.refresh()
        advanceUntilIdle()

        val state = assertIs<ServiceStatusViewModel.UiState.Ready>(vm.uiState.value)
        assertEquals(1, state.snapshot.allMonitors.size)
        assertEquals(RefreshState.IDLE, state.refreshState)
        assertEquals(AhuError.Timeout.toUserMessage(), state.refreshError)

        gateway.result = AhuResult.Success(sampleSnapshot(up = false))
        vm.refresh()
        advanceTimeBy(100)

        val updated = assertIs<ServiceStatusViewModel.UiState.Ready>(vm.uiState.value)
        assertEquals(RefreshState.UPDATED, updated.refreshState)
        assertNull(updated.refreshError)
        assertEquals(ServiceHealth.DOWN, updated.snapshot.overallHealth)

        advanceUntilIdle()
        val settled = assertIs<ServiceStatusViewModel.UiState.Ready>(vm.uiState.value)
        assertEquals(RefreshState.IDLE, settled.refreshState)
    }

    @Test
    fun `formats utc timestamps to target timezone and formats percentages cleanly`() {
        assertEquals(
            "13:12",
            ServiceStatusViewModel.formatUtcTimestampToLocal(
                "2026-11-23 05:12:07.682",
                ZoneId.of("Asia/Shanghai")
            )
        )
        assertEquals("100%", ServiceStatusViewModel.formatPercent(1.0))
        assertEquals("99.5%", ServiceStatusViewModel.formatPercent(0.995))
        assertEquals("0%", ServiceStatusViewModel.formatPercent(0.0))
    }

    private fun sampleSnapshot(up: Boolean = true) = ServiceStatusSnapshot(
        title = "安大通服务状态",
        autoRefreshSeconds = 300,
        incidents = emptyList(),
        groups = listOf(
            ServiceStatusGroup(
                id = 1,
                name = "软件业务服务",
                monitors = listOf(
                    ServiceStatusMonitor(
                        id = 2,
                        name = "软件更新服务器",
                        type = "http",
                        health = if (up) ServiceHealth.UP else ServiceHealth.DOWN,
                        uptime24h = 1.0,
                        latestPingMs = 15,
                        errorRate = null,
                        lastCheckTime = "2026-11-23 05:12:07.682"
                    )
                )
            )
        )
    )
}
