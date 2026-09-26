package com.ahu.ahutong.data.adapter

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.model.ycard.ElectricityUsageHistoryPage
import com.ahu.ahutong.data.recharge.ElectricityDailyUsage
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class ElectricityUsagePaginationTest {
    @Test
    fun `HAR complete response without total makes exactly one history request`() = runTest {
        var calls = 0
        val rows = (1..27).map { reading(it) }
        val result = collectElectricityUsagePages {
            calls++
            AhuResult.Success(ElectricityUsageHistoryPage(rows, null))
        }

        assertEquals(1, calls)
        assertEquals(rows, assertIs<AhuResult.Success<List<ElectricityDailyUsage>>>(result).value)
    }

    @Test
    fun `fetches all advertised pages and retains different same day readings`() = runTest {
        val pages = listOf(
            listOf(reading(1), reading(2)),
            listOf(reading(2, 3.45), reading(3))
        )
        val requested = mutableListOf<Int>()
        val result = collectElectricityUsagePages { page ->
            requested += page
            AhuResult.Success(ElectricityUsageHistoryPage(pages[page - 1], 4))
        }

        assertEquals(listOf(1, 2), requested)
        assertEquals(pages.flatten(), assertIs<AhuResult.Success<List<ElectricityDailyUsage>>>(result).value)
    }

    @Test
    fun `a repeated server page is rejected instead of inflating consumption`() = runTest {
        var calls = 0
        val result = collectElectricityUsagePages {
            calls++
            AhuResult.Success(ElectricityUsageHistoryPage(listOf(reading(1)), 4))
        }

        assertEquals(2, calls)
        assertIs<AhuError.ProtocolChanged>(assertIs<AhuResult.Failure>(result).error)
    }

    @Test
    fun `empty final page and changed total fail without returning partial history`() = runTest {
        for (secondPage in listOf(
            ElectricityUsageHistoryPage(emptyList(), 4),
            ElectricityUsageHistoryPage(listOf(reading(2)), 5),
            ElectricityUsageHistoryPage(listOf(reading(2)), null)
        )) {
            val result = collectElectricityUsagePages { page ->
                AhuResult.Success(
                    if (page == 1) ElectricityUsageHistoryPage(listOf(reading(1)), 4) else secondPage
                )
            }

            assertIs<AhuError.ProtocolChanged>(assertIs<AhuResult.Failure>(result).error)
        }
    }

    @Test
    fun `authentication or network failure propagates without an estimated balance`() = runTest {
        val failure = AhuResult.Failure(AhuError.Network)
        val result = collectElectricityUsagePages { page ->
            if (page == 1) AhuResult.Success(ElectricityUsageHistoryPage(listOf(reading(1)), 4))
            else failure
        }

        assertSame(failure, result)
    }

    @Test
    fun `too many rows for the advertised total reject the inconsistent history`() = runTest {
        val result = collectElectricityUsagePages { page ->
            AhuResult.Success(
                ElectricityUsageHistoryPage(listOf(reading(page * 2 - 1), reading(page * 2)), 3)
            )
        }

        assertIs<AhuError.ProtocolChanged>(assertIs<AhuResult.Failure>(result).error)
    }

    @Test
    fun `an implausibly long history is capped at twenty pages`() = runTest {
        var calls = 0
        val result = collectElectricityUsagePages { page ->
            calls++
            AhuResult.Success(ElectricityUsageHistoryPage(listOf(reading(page)), 100))
        }

        assertEquals(20, calls)
        assertIs<AhuError.ProtocolChanged>(assertIs<AhuResult.Failure>(result).error)
    }

    private fun reading(day: Int, kwh: Double = 2.0) =
        ElectricityDailyUsage(LocalDate.of(2026, 9, day), kwh)
}
