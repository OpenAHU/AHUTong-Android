package com.ahu.ahutong.data.adapter

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.recharge.ElectricityDailyUsage
import com.ahu.ahutong.data.recharge.ElectricityUsagePredictor
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ElectricityUsageSnapshotTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun `zero or negative balance can warn even when history is unavailable`() = runTest {
        for (balance in listOf(0.0, -2.0)) {
            val result = loadElectricityUsageSnapshot(today, balance) { error("history must not be requested") }
            val snapshot = requireNotNull(result.valueOrNull())
            assertEquals(balance, snapshot.remainingKwh)
            assertTrue(snapshot.dailyUsage.isEmpty())
            assertEquals(today, ElectricityUsagePredictor.predict(snapshot, 3).nextCheckDate)
        }
    }

    @Test fun `a positive balance still requires complete history`() = runTest {
        val failure = AhuResult.Failure(AhuError.Timeout)
        assertEquals(failure, loadElectricityUsageSnapshot(today, 2.0) { failure })
    }

    @Test fun `forecast uses only the thirty complete days before today`() = runTest {
        val included = ElectricityDailyUsage(today.minusDays(30), 10.0)
        val result = loadElectricityUsageSnapshot(today, 20.0) {
            AhuResult.Success(listOf(included,
                ElectricityDailyUsage(today.minusDays(31), 1.0), ElectricityDailyUsage(today, 100.0)))
        }
        assertEquals(listOf(included), result.valueOrNull()?.dailyUsage)
    }
}
