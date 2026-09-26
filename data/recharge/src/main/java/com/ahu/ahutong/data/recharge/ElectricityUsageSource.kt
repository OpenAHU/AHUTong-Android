package com.ahu.ahutong.data.recharge

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.RoomSelectionInfo
import java.time.LocalDate

/** A balance and the preceding 30 complete days, expressed in kWh throughout. */
interface ElectricityUsageSource {
    suspend fun snapshot(
        controller: ElectricityController,
        selection: RoomSelectionInfo,
        asOf: LocalDate
    ): AhuResult<ElectricityUsageSnapshot>
}

data class ElectricityDailyUsage(val date: LocalDate, val kwh: Double)

data class ElectricityUsageSnapshot(
    val asOf: LocalDate,
    val remainingKwh: Double,
    val dailyUsage: List<ElectricityDailyUsage>,
    val periodStart: LocalDate,
    val periodEnd: LocalDate
)

data class ElectricityUsageForecast(
    val dailyKwh: Double,
    val daysRemaining: Double?,
    val nextCheckDate: LocalDate,
    val zeroDate: LocalDate?,
    /** Cumulative risk quantities for horizons 1..30, in kWh. */
    val riskKwhByDays: List<Double> = emptyList(),
    val method: String = "mean"
)
