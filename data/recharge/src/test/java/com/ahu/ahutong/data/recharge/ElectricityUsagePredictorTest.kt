package com.ahu.ahutong.data.recharge

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElectricityUsagePredictorTest {
    private val today = LocalDate.of(2026, 9, 26)

    @Test
    fun `forecasts the date for confirming a fractional threshold crossing`() {
        val forecast = ElectricityUsagePredictor.predict(
            snapshot(remaining = 16.0, readings = completeHistory(2.0)),
            thresholdDays = 3
        )

        assertEquals(2.0, forecast.dailyKwh)
        assertEquals(8.0, forecast.daysRemaining)
        assertEquals(today.plusDays(4), forecast.nextCheckDate)
        assertEquals(today.plusDays(8), forecast.zeroDate)

        val fractional = ElectricityUsagePredictor.predict(
            snapshot(remaining = 15.0, readings = completeHistory(2.0)), 3
        )
        assertEquals(today.plusDays(4), fractional.nextCheckDate)
    }

    @Test
    fun `uses calendar days for sparse B readings rather than their count`() {
        val readings = listOf(
            ElectricityDailyUsage(today.minusDays(30), 1.0),
            ElectricityDailyUsage(today.minusDays(20), 20.0),
            ElectricityDailyUsage(today.minusDays(10), 9.0),
            ElectricityDailyUsage(today.minusDays(1), 7.0)
        )
        val forecast = ElectricityUsagePredictor.predict(snapshot(12.0, readings), 3)

        val withZeroDays = (1L..30L).map { offset ->
            val date = today.minusDays(offset)
            ElectricityDailyUsage(date, readings.firstOrNull { it.date == date }?.kwh ?: 0.0)
        }
        assertEquals(ElectricityUsagePredictor.predict(snapshot(12.0, withZeroDays), 3), forecast)
    }

    @Test
    fun `adds duplicate day readings and accounts for increasing recent use`() {
        val readings = completeHistory(1.0) + listOf(
            ElectricityDailyUsage(today.minusDays(1), 2.07),
            ElectricityDailyUsage(today.minusDays(1), 3.45)
        )
        val forecast = ElectricityUsagePredictor.predict(snapshot(10.0, readings), 3)

        assertTrue(forecast.dailyKwh > 1.0)
        assertTrue(forecast.riskKwhByDays[2] >= forecast.dailyKwh * 3)
    }

    @Test
    fun `ignores today incomplete readings and records outside the history window`() {
        val readings = completeHistory(1.0) + listOf(
            ElectricityDailyUsage(today, 200.0),
            ElectricityDailyUsage(today.minusDays(31), 100.0)
        )
        val forecast = ElectricityUsagePredictor.predict(snapshot(5.0, readings), 3)

        assertEquals(1.0, forecast.dailyKwh)
        assertEquals(5.0, forecast.daysRemaining)
    }

    @Test
    fun `empty and all zero history schedule another check without inventing usage`() {
        for (readings in listOf(emptyList(), completeHistory(0.0))) {
            val forecast = ElectricityUsagePredictor.predict(snapshot(5.0, readings), 3)

            assertEquals(0.0, forecast.dailyKwh)
            assertNull(forecast.daysRemaining)
            assertNull(forecast.zeroDate)
            assertEquals(today.plusDays(1), forecast.nextCheckDate)
        }
    }

    @Test
    fun `an observed zero balance is already due even without usable history`() {
        val forecast = ElectricityUsagePredictor.predict(snapshot(0.0, emptyList()), 3)

        assertEquals(0.0, forecast.daysRemaining)
        assertEquals(today, forecast.zeroDate)
        assertEquals(today, forecast.nextCheckDate)
    }

    @Test
    fun `threshold equality is due today and a recovered balance moves the next check`() {
        val readings = completeHistory(2.0)
        val due = ElectricityUsagePredictor.predict(snapshot(6.0, readings), 3)
        val recovered = ElectricityUsagePredictor.predict(snapshot(60.0, readings), 3)

        assertEquals(3.0, due.daysRemaining)
        assertEquals(today, due.nextCheckDate)
        assertEquals(30.0, recovered.daysRemaining)
        assertEquals(today.plusDays(26), recovered.nextCheckDate)
    }

    @Test
    fun `an extreme finite value cannot overflow the calendar`() {
        val forecast = ElectricityUsagePredictor.predict(
            snapshot(Double.MAX_VALUE, completeHistory(1.0)), 3
        )

        assertEquals(today.plusDays(30), forecast.nextCheckDate)
        assertNull(forecast.zeroDate)
    }

    @Test
    fun `rejects invalid usage and unsupported thresholds`() {
        assertFailsWith<IllegalArgumentException> {
            ElectricityUsagePredictor.predict(snapshot(5.0, completeHistory(-1.0)), 3)
        }
        assertFailsWith<IllegalArgumentException> {
            ElectricityUsagePredictor.predict(snapshot(Double.NaN, emptyList()), 3)
        }
        assertFailsWith<IllegalArgumentException> {
            ElectricityUsagePredictor.predict(snapshot(5.0, emptyList()), 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ElectricityUsagePredictor.predict(snapshot(5.0, emptyList()), 31)
        }
    }

    private fun completeHistory(kwh: Double) = (1L..30L).map {
        ElectricityDailyUsage(today.minusDays(it), kwh)
    }

    private fun snapshot(remaining: Double, readings: List<ElectricityDailyUsage>) =
        ElectricityUsageSnapshot(today, remaining, readings, today.minusDays(30), today.minusDays(1))
}
