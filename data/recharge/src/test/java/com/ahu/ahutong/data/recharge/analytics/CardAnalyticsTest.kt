package com.ahu.ahutong.data.recharge.analytics

import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 分析引擎单测（纯 JVM，无 Android 依赖）。
 * 覆盖最容易写错的几处：学期边界、typeFrom 支出口径、逻辑日 04:00、趋势补零。
 */
class CardAnalyticsTest {

    @Test
    fun `semester analysis uses academic calendar boundaries`() {
        val bills = listOf(
            expense("2026-02-20 12:00:00", "before"), // 开学前
            expense("2026-02-24 12:00:00", "inside")  // 开学后
        )
        val semester = AcademicSemester(
            id = 112,
            nameZh = "2025-2026学年第二学期",
            code = "2025-2026-2",
            startDate = "2026-02-23",
            endDate = "2026-07-05"
        )

        val report = bills.toAnalyticsReport(
            today = date(2026, 3, 1),
            academicSemesters = listOf(semester)
        )

        val period = report.semesterPeriods.single()
        assertEquals("2026-02-23", period.startDay)
        assertEquals("2026-07-05", period.endDay)
        assertEquals(1, report.summary(report.currentSemesterId)?.expenseCount)
    }

    @Test
    fun `semester analysis is unavailable without registrar dates`() {
        val report = listOf(expense("2026-03-01 12:00:00", "x"))
            .toAnalyticsReport(today = date(2026, 3, 1))
        assertTrue(report.semesterPeriods.isEmpty())
    }

    @Test
    fun `expense direction comes from typeFrom not keywords`() {
        // 浴室扣款：文案无「消费/支付」关键字，typeFrom="2" 必须判为支出
        val bathroom = TurnoverRecord(
            orderId = "b1",
            tranamt = 300,
            typeFrom = "2",
            turnoverType = null,
            resume = "浴室水控扣款",
            toMerchant = "北二区浴室",
            effectdateStr = "2026-09-10 22:00:00"
        )
        // 充值：typeFrom="1" 必须排除
        val recharge = TurnoverRecord(
            orderId = "r1",
            tranamt = 10000,
            typeFrom = "1",
            turnoverType = "充值",
            resume = "校园卡充值",
            effectdateStr = "2026-09-10 08:00:00"
        )
        val report = listOf(bathroom, recharge).toAnalyticsReport(today = date(2026, 9, 22))
        val month = report.currentMonth
        assertNotNull(month)
        assertEquals(1, month.expenseCount)
        assertEquals(300, month.totalExpense)
    }

    @Test
    fun `logical day rolls pre-dawn spending back to previous day`() {
        // 凌晨 2 点的消费（04:00 分界前）应归前一天
        val nightOwl = expense("2026-09-15 02:30:00", "n1")
        val report = listOf(nightOwl).toAnalyticsReport(today = date(2026, 9, 22))
        val month = report.currentMonth
        assertNotNull(month)
        assertTrue(month.transactionsByDay.containsKey("2026-09-14"))
    }

    @Test
    fun `daily trend zero-fills days without spending`() {
        val bills = listOf(
            expense("2026-09-01 12:00:00", "a"),
            expense("2026-09-03 12:00:00", "b")
        )
        val report = bills.toAnalyticsReport(today = date(2026, 9, 3))
        val trend = report.currentMonth?.dailyTrend.orEmpty()
        // 趋势铺满全月（30 个点），2 号为补零日
        assertEquals(30, trend.size)
        assertEquals(0, trend[1].totalExpense)
        assertEquals("2026-09-02", trend[1].date)
    }

    private fun expense(time: String, id: String) = TurnoverRecord(
        orderId = id,
        tranamt = 100,
        typeFrom = "2",
        turnoverType = "二维码支付",
        resume = "北二区食堂一楼-扫码支付",
        toMerchant = "北二区食堂一楼",
        effectdateStr = time
    )

    private fun date(year: Int, month: Int, day: Int): Date {
        val cal = Calendar.getInstance(Locale.CHINA)
        cal.clear()
        cal.set(year, month - 1, day, 12, 0, 0)
        return cal.time
    }
}
