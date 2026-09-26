package com.ahu.ahutong.data.recharge

import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElectricityForecastModelTest {
    @Test fun `SES and damped Holt agree with hand calculated published recurrences`() {
        val data = doubleArrayOf(2.0, 6.0, 4.0)
        val ses = ElectricityForecastModel("ses", alpha = 0.5)
        assertEquals(4.0, ses.fit(data).level)
        val holt = ElectricityForecastModel("damped_holt", 0.5, 0.2, 0.9)
        val state = holt.fit(data)
        assertEquals(4.18, state.level, 1e-10)
        assertEquals(0.324, state.trend, 1e-10)
        val forecast = holt.path(state, 2)
        assertEquals(4.4716, forecast[0], 1e-10)
        assertEquals(4.73404, forecast[1], 1e-10)
    }

    @Test fun `TSB lowers occurrence after zero days and retains its unobserved future mean`() {
        val model = ElectricityForecastModel("tsb", 0.5, 0.25)
        val state = model.fit(doubleArrayOf(0.0, 4.0, 0.0, 8.0, 0.0))
        assertEquals(6.0, state.level)
        assertEquals(0.3984375, state.probability)
        assertEquals(List(10) { 2.390625 }, model.path(state, 10).toList())
        state.observe(model, 0.0)
        assertEquals(0.298828125, state.probability)
        assertEquals(6.0, state.level)
    }

    @Test fun `Holt preserves the published raw state when only emitted consumption is clipped`() {
        val model = ElectricityForecastModel("damped_holt", 0.5, 0.2, 0.9)
        val state = ElectricityForecastModel.State(1.0, -2.0)
        assertEquals(0.0, state.forecast(model))
        state.observe(model, 0.0)
        assertEquals(-0.4, state.level, 1e-10)
        assertEquals(-1.72, state.trend, 1e-10)
        assertEquals(List(3) { 0.0 }, model.path(state, 3).toList())
        assertEquals(-0.4, state.level, 1e-10)
    }

    @Test fun `constant series retains the simplest model and exact cumulative consumption`() {
        val values = DoubleArray(30) { 2.0 }
        val model = ElectricityForecastModel.select(values)
        assertEquals("mean", model.method)
        val central = DoubleArray(30) { 2.0 * (it + 1) }
        assertEquals(central.toList(), model.riskCurve(values, 30, central).toList())
    }

    @Test fun `adaptive point forecasts improve held out rising consumption over the old mean`() {
        val training = DoubleArray(30) { 2.0 + it * 0.3 }
        val actual = DoubleArray(7) { 2.0 + (30 + it) * 0.3 }.sum()
        val model = ElectricityForecastModel.select(training)
        val predicted = model.path(model.fit(training), 7).sum()
        val baseline = ElectricityForecastModel.legacyRate(training) * 7
        assertEquals("damped_holt", model.method)
        assertTrue(abs(predicted - actual) < abs(baseline - actual) * 0.5)
    }

    @Test fun `risk reflects intermittent positive use and is deterministic and nondecreasing`() {
        val values = DoubleArray(30) { if (it % 3 == 0) 9.0 else 0.0 }
        val model = ElectricityForecastModel("tsb", 0.2, 0.15)
        val rate = model.fit(values).forecast(model)
        val central = DoubleArray(30) { rate * (it + 1) }
        val risk = model.riskCurve(values, 30, central)
        assertEquals(risk.toList(), model.riskCurve(values, 30, central).toList())
        assertTrue(risk[2] > central[2])
        assertTrue(risk.toList().zipWithNext().all { (a, b) -> b >= a })
        assertTrue(risk.all { it.isFinite() && it >= 0.0 })
    }

    @Test fun `forecast does not extrapolate negative consumption or overflow near the calendar limit`() {
        val date = LocalDate.MAX.minusDays(1)
        val readings = (1L..30L).map { ElectricityDailyUsage(date.minusDays(it), 2.0) }
        val result = ElectricityUsagePredictor.predict(
            ElectricityUsageSnapshot(date, 1e100, readings, date.minusDays(30), date.minusDays(1)), 30)
        assertEquals(LocalDate.MAX, result.nextCheckDate)
        assertTrue(result.riskKwhByDays.all { it.isFinite() && it >= 0.0 })
    }

    @Test fun `HAR histories compare old and adaptive forecasts on unseen three day totals`() {
        // Only anonymized daily kWh aggregates from the user-supplied HARs; no room or account data.
        val histories = mapOf(
            "A" to doubleArrayOf(0.0,0.0,0.0,0.0,0.0,5.56,10.54,13.65,9.83,6.9,6.77,9.39,4.5,5.53,8.58,4.1,5.52,1.9,5.37,4.42,3.76,4.36,0.77,0.12,1.67,5.11,4.24,3.99,6.68,3.88),
            "B" to doubleArrayOf(0.5,0.0,0.0,0.0,4.7,0.0,4.86,2.41,2.17,0.0,0.0,0.0,9.31,0.0,0.0,0.0,0.0,0.0,16.14,0.07,5.12,3.65,0.0,0.0,7.06,0.0,0.0,10.06,3.63,0.0),
            "C" to doubleArrayOf(3.66,3.12,2.59,3.86,4.3,2.88,2.89,4.12,4.94,6.79,6.0,5.6,4.82,5.36,2.95,4.3,0.0,5.71,6.05,3.56,3.87,3.48,6.16,5.21,3.91,4.35,3.52,3.5,5.05,3.25)
        )
        for ((label, values) in histories) {
            assertEquals(30, values.size)
            var oldError = 0.0
            var newError = 0.0
            for (origin in 21..27) {
                val prefix = values.copyOf(origin)
                val model = ElectricityForecastModel.select(prefix)
                val actual = values.sliceArray(origin until origin + 3).sum()
                oldError += abs(ElectricityForecastModel.legacyRate(prefix) * 3 - actual) / 3
                newError += abs(model.path(model.fit(prefix), 3).sum() - actual) / 3
            }
            val chosen = ElectricityForecastModel.select(values)
            println("HAR $label: nested rolling MAE/day old=${oldError / 7}, new=${newError / 7}, model=$chosen")
            assertTrue(oldError.isFinite() && newError.isFinite())
        }
    }
}
