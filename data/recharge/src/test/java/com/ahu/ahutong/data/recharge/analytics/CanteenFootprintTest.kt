package com.ahu.ahutong.data.recharge.analytics

import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanteenFootprintTest {

    private fun record(
        dateTime: String,
        terminal: String?,
        fen: Long,
        merchant: String = "北二区食堂一楼-扫码支付"
    ) = TurnoverRecord(
        orderId = "$dateTime-$terminal-$fen-${fen.hashCode()}${dateTime.hashCode()}",
        tranamt = fen,
        typeFrom = "2",
        resume = merchant,
        toMerchant = merchant,
        effectdateStr = dateTime,
        locationName = terminal
    )

    /** 固定「今天」：2026-10-06（周二）18:00，保证热力图右端确定。 */
    private val fixedToday: Date = Calendar.getInstance(Locale.CHINA).apply {
        clear(); set(2026, 9, 6, 18, 0)
    }.time

    @Test
    fun `only meal slots are counted`() {
        val records = listOf(
            record("2026-10-06 07:30:00", "77-139", 400),   // 早餐：排除
            record("2026-10-06 10:30:00", "77-139", 1200),  // 午餐边界：计入
            record("2026-10-06 12:05:00", "77-140", 1300),  // 午餐：计入
            record("2026-10-06 14:30:00", "77-139", 500),   // 下午：排除
            record("2026-10-06 16:30:00", "77-139", 1100),  // 晚餐边界：计入
            record("2026-10-06 21:40:00", "77-140", 800)    // 夜宵并入晚餐：计入
        )
        val fp = records.toCanteenFootprint(today = fixedToday)
        assertEquals(4, fp.mealCount)
        assertEquals(1200 + 1300 + 1100 + 800L, fp.totalFen)
    }

    @Test
    fun `consecutive taps within 5 minutes merge into one meal`() {
        val records = listOf(
            record("2026-10-05 12:00:00", "77-139", 1000),
            record("2026-10-05 12:03:00", "77-139", 200),   // +3min：同一餐
            record("2026-10-05 12:04:30", "77-139", 100),   // 距首笔 4.5min：同一餐
            record("2026-10-05 12:12:00", "77-139", 900)    // 距首笔 12min：新的一餐
        )
        val fp = records.toCanteenFootprint(today = fixedToday)
        assertEquals(2, fp.mealCount)
        assertEquals(2200, fp.totalFen)
    }

    @Test
    fun `non canteen merchants are excluded`() {
        val records = listOf(
            record("2026-10-05 12:00:00", "77-139", 1000),                      // 食堂：计入
            record("2026-10-05 12:10:00", null, 500, "天猫超市"),               // 超市：排除
            record("2026-10-05 18:00:00", null, 2000, "电控缴费-桔园"),          // 电费：排除
            TurnoverRecord(orderId = "x1", tranamt = 5000, typeFrom = "1",
                resume = "充值", effectdateStr = "2026-10-05 09:00:00")         // 充值：排除
        )
        val fp = records.toCanteenFootprint(today = fixedToday)
        assertEquals(1, fp.mealCount)
    }

    @Test
    fun `window ranking with mapped names and streaks`() {
        // 烤盘饭 77-139：10-03/04/05 连续三天各一餐（连击 3）；麻辣烫 77-140：两天各一餐
        val records = listOf(
            record("2026-10-03 12:00:00", "77-139", 1300),
            record("2026-10-04 12:00:00", "77-139", 1300),
            record("2026-10-05 12:00:00", "77-139", 1300),
            record("2026-10-03 18:00:00", "77-140", 1500),
            record("2026-10-05 18:00:00", "77-140", 1500),
            record("2026-10-05 12:30:00", null, 1000)  // 无终端码：算餐数不上榜
        )
        val fp = records.toCanteenFootprint(
            windowNames = mapOf("77-139" to "烤盘饭"),
            today = fixedToday
        )
        assertEquals(6, fp.mealCount)
        assertEquals(5, fp.coveredMeals)          // 覆盖率 5/6
        assertEquals(2, fp.windowCount)

        val top = fp.topWindows.first()
        assertEquals("77-139", top.terminal)
        assertEquals("烤盘饭", top.windowName)
        assertEquals("榴园", top.canteen)
        assertEquals(3, top.mealCount)
        assertEquals(3, top.currentStreak)         // 10-03/04/05 连击
        assertEquals(1300, top.avgFen)

        val second = fp.topWindows[1]
        assertEquals(null, second.windowName)      // 未收录 → 求认领
        assertEquals(1, second.currentStreak)
    }

    @Test
    fun `heatmap columns align to monday and end at current week`() {
        val records = listOf(
            record("2026-10-05 12:00:00", "77-139", 1000),  // 周一 1 餐
            record("2026-10-06 12:00:00", "77-139", 1000),  // 周二 1 餐
            record("2026-10-06 18:00:00", "77-140", 1000)   // 周二共 2 餐
        )
        val fp = records.toCanteenFootprint(weeksBack = 2, today = fixedToday)
        assertEquals(2, fp.weeks.size)                       // 本周 + 上一周
        assertEquals(7, fp.weeks.last().size)
        // 2026-10-05 是周一：最后一列（本周）的第 0 格=1 餐，第 1 格（周二）=2 餐
        assertEquals(1, fp.weeks.last()[0])
        assertEquals(2, fp.weeks.last()[1])
        assertEquals(0, fp.weeks.last()[2])                  // 周三（未来）=0
        assertEquals("2026-10-05", fp.weekStartDays.last())
    }

    @Test
    fun `badges computed`() {
        val records = listOf(
            record("2026-10-03 12:00:00", "77-139", 800),   // 周六午
            record("2026-10-03 18:00:00", "77-139", 800),   // 周六晚（凑满 3 餐参评「最省」）
            record("2026-10-04 12:00:00", "77-139", 800),   // 周日
            record("2026-10-05 12:00:00", "77-141", 900),   // 本周一新窗口
            record("2026-10-06 12:00:00", "77-141", 900)
        )
        val fp = records.toCanteenFootprint(today = fixedToday)
        assertEquals(3, fp.weekendMealCount)               // 周末坚守：六午+六晚+日午
        assertEquals(1, fp.newWindowsThisWeek)             // 本周尝新：77-141
        val cheapest = fp.cheapestWindow
        assertNotNull(cheapest)
        assertEquals("77-139", cheapest.terminal)          // 均价 800 < 900
    }

    @Test
    fun `empty input yields empty footprint`() {
        val fp = emptyList<TurnoverRecord>().toCanteenFootprint(today = fixedToday)
        assertEquals(0, fp.mealCount)
        assertTrue(fp.weeks.isEmpty())
        assertNull(fp.cheapestWindow)
    }

    @Test
    fun `daily stats aggregate per terminal and day with slots`() {
        val records = listOf(
            record("2026-10-05 12:00:00", "77-139", 1000),
            record("2026-10-05 12:03:00", "77-139", 300),   // 合并为一餐
            record("2026-10-05 18:30:00", "77-139", 1500),  // 同日同终端晚餐
            record("2026-10-06 12:00:00", "77-139", 1200),  // 另一天
            record("2026-10-05 12:10:00", null, 900),       // 无终端码：不上传
            record("2026-10-05 07:30:00", "77-139", 400)    // 早餐：不上传
        )
        val stats = records.toDailyCanteenStats()
        assertEquals(2, stats.size)

        val day1 = stats.first { it.day == "2026-10-05" }
        assertEquals("77-139", day1.terminal)
        assertEquals(2, day1.meals)
        assertEquals(1, day1.mealsLunch)
        assertEquals(1, day1.mealsDinner)
        assertEquals(2800, day1.amountCents)
        assertEquals("榴园", day1.canteen)

        val day2 = stats.first { it.day == "2026-10-06" }
        assertEquals(1, day2.meals)
        assertEquals(1200, day2.amountCents)
    }
}
