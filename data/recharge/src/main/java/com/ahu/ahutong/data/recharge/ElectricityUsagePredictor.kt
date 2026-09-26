package com.ahu.ahutong.data.recharge

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Rolling-origin model selection and empirical cumulative-consumption risk forecasts. */
object ElectricityUsagePredictor {
    const val ALGORITHM_VERSION = 2

    fun predict(snapshot: ElectricityUsageSnapshot, thresholdDays: Int): ElectricityUsageForecast {
        require(thresholdDays in 1..30)
        require(snapshot.remainingKwh.isFinite())
        require(snapshot.periodEnd.isBefore(snapshot.asOf))
        val days = ChronoUnit.DAYS.between(snapshot.periodStart, snapshot.periodEnd) + 1
        require(days in 1L..30L)
        require(snapshot.dailyUsage.all { it.kwh.isFinite() && it.kwh >= 0.0 })
        val daily = snapshot.dailyUsage
            .filter { !it.date.isBefore(snapshot.periodStart) && !it.date.isAfter(snapshot.periodEnd) }
            .groupBy { it.date }
            .mapValues { (_, readings) -> readings.sumOf { it.kwh } }
        require(daily.values.all { it.isFinite() })
        // A successful complete response establishes zero-use room days where no row is present.
        val values = DoubleArray(days.toInt()) { daily[snapshot.periodStart.plusDays(it.toLong())] ?: 0.0 }
        require(values.sum().isFinite())
        val model = ElectricityForecastModel.select(values)
        val usage = model.path(model.fit(values), 90)
        val cumulative = DoubleArray(90)
        for (index in usage.indices) cumulative[index] = usage[index] + (cumulative.getOrNull(index - 1) ?: 0.0)
        require(usage.all { it.isFinite() && it >= 0.0 } && cumulative.all { it.isFinite() })
        val rate = usage.take(thresholdDays).average()
        fun dateAfter(count: Int): LocalDate = snapshot.asOf.plusDays(
            count.toLong().coerceAtMost(ChronoUnit.DAYS.between(snapshot.asOf, LocalDate.MAX))
        )
        if (snapshot.remainingKwh <= 0.0) {
            return ElectricityUsageForecast(rate, 0.0, snapshot.asOf, snapshot.asOf,
                List(30) { 0.0 }, model.method)
        }
        if (values.all { it == 0.0 }) {
            return ElectricityUsageForecast(0.0, null, dateAfter(1), null,
                List(30) { 0.0 }, model.method)
        }
        val risk = model.riskCurve(values, 60, cumulative)
        require(risk.all { it.isFinite() && it >= 0.0 })
        // Day k includes consumption before k and the following threshold days in the same path.
        val crossing = (0..30).firstOrNull { risk[it + thresholdDays - 1] >= snapshot.remainingKwh }
        val checkAfter = crossing?.let { (it - 1).coerceAtLeast(0) } ?: 30
        val zeroIndex = cumulative.indexOfFirst { it >= snapshot.remainingKwh }
        val duration = if (zeroIndex < 0) null else {
            val previous = cumulative.getOrNull(zeroIndex - 1) ?: 0.0
            zeroIndex + (snapshot.remainingKwh - previous) / usage[zeroIndex]
        }
        return ElectricityUsageForecast(
            rate, duration, dateAfter(checkAfter), duration?.let { dateAfter(ceil(it).toInt()) },
            risk.take(30), model.method
        )
    }
}
