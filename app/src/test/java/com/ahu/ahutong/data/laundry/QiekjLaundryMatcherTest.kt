package com.ahu.ahutong.data.laundry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QiekjLaundryMatcherTest {

    /** 实测龙河点位列表（20 个，含平台原文错字「傅士5楼」）。 */
    private val points = listOf(
        "301幢洗衣房", "外招楼东楼", "外招楼北楼", "内招楼", "203幢", "202幢", "201幢",
        "300幢", "306幢洗衣房", "204栋", "205栋1楼洗衣机", "205栋", "403栋", "402栋",
        "401栋", "209幢", "206幢洗衣房", "博士1楼", "博士3楼", "傅士5楼"
    )

    @Test
    fun `楼栋号唯一命中机房`() {
        assertEquals(LaundryParkMatch.Hit(13), QiekjLaundryMatcher.match("402栋", points))
        assertEquals(LaundryParkMatch.Hit(9), QiekjLaundryMatcher.match("204栋", points))
    }

    @Test
    fun `校区前缀与全角数字不影响匹配`() {
        assertEquals(LaundryParkMatch.Hit(13), QiekjLaundryMatcher.match("龙河校区402栋", points))
        assertEquals(LaundryParkMatch.Hit(5), QiekjLaundryMatcher.match("２０２幢", points))
    }

    @Test
    fun `同楼栋多机房给候选不猜`() {
        assertEquals(
            LaundryParkMatch.Multiple(listOf(10, 11)),
            QiekjLaundryMatcher.match("205栋", points)
        )
    }

    @Test
    fun `认不出返回 None`() {
        assertEquals(LaundryParkMatch.None, QiekjLaundryMatcher.match(null, points))
        assertEquals(LaundryParkMatch.None, QiekjLaundryMatcher.match("博士1楼", points))
        assertEquals(LaundryParkMatch.None, QiekjLaundryMatcher.match("402栋", emptyList()))
    }

    @Test
    fun `机位与楼层兼容两种顺序`() {
        assertEquals("602", QiekjLaundryMatcher.slotOf("602号1楼203栋"))
        assertEquals(1, QiekjLaundryMatcher.floorOf("602号1楼203栋"))
        assertEquals("155", QiekjLaundryMatcher.slotOf("402幢1楼155号"))
        assertEquals(1, QiekjLaundryMatcher.floorOf("402幢1楼155号"))
        assertEquals("513", QiekjLaundryMatcher.slotOf("513号"))
        assertNull(QiekjLaundryMatcher.slotOf(null))
        assertNull(QiekjLaundryMatcher.floorOf("513号"))
    }

    @Test
    fun `机位号兼容号前空格与无号写法`() {
        // 300幢 3 楼这台平台写成「787 号3楼」，号前带空格；以前解析失败后被补成「1」
        assertEquals("787", QiekjLaundryMatcher.slotOf("787 号3楼"))
        assertEquals(3, QiekjLaundryMatcher.floorOf("787 号3楼"))
        assertEquals("787", QiekjLaundryMatcher.slotOf("787\u3000号3楼"))
        // 博士楼 / 306 洗衣房不写「号」，机位号直接跟在楼层后
        assertEquals("659", QiekjLaundryMatcher.slotOf("博士楼-1楼659"))
        assertEquals(1, QiekjLaundryMatcher.floorOf("博士楼-1楼659"))
        assertEquals("206", QiekjLaundryMatcher.slotOf("306幢 206"))
        assertEquals("754", QiekjLaundryMatcher.slotOf("博士楼-5楼754"))
        // 只有楼层、本来没有机位号的不许编造
        assertNull(QiekjLaundryMatcher.slotOf("300幢4楼"))
        assertNull(QiekjLaundryMatcher.slotOf("209栋2楼洗鞋机"))
        // 带「号」的仍取机位号，不取楼栋号
        assertEquals("756", QiekjLaundryMatcher.slotOf("756号4楼300幢"))
        assertEquals("762", QiekjLaundryMatcher.slotOf("762号300幢2楼"))
    }

    @Test
    fun `剩余时间文案换算成毫秒`() {
        assertEquals(1_200_000L, QiekjLaundryMatcher.remainingMillis("20分钟"))
        assertEquals(49_000L, QiekjLaundryMatcher.remainingMillis("49秒"))
        assertEquals(3_900_000L, QiekjLaundryMatcher.remainingMillis("1小时5分"))
        assertNull(QiekjLaundryMatcher.remainingMillis(""))
        assertNull(QiekjLaundryMatcher.remainingMillis(null))
    }

    @Test
    fun `干衣机楼栋楼层与机位混合写法`() {
        assertEquals(3, QiekjLaundryMatcher.floorOf("300幢3楼211号"))
        assertEquals(2, QiekjLaundryMatcher.floorOf("300幢二楼536号"))
        assertEquals(4, QiekjLaundryMatcher.floorOf("300幢四楼528号"))
        assertEquals("536", QiekjLaundryMatcher.slotOf("300幢二楼536号"))
        assertEquals("300幢", QiekjLaundryMatcher.buildingLabel("300幢二楼536号"))
        assertEquals("300", QiekjLaundryMatcher.buildingOf("300幢二楼536号"))
        assertEquals(1, QiekjLaundryMatcher.floorOf("697号1楼"))
        assertNull(QiekjLaundryMatcher.buildingLabel("697号1楼"))
    }

    @Test
    fun `状态码换算占用`() {
        assertEquals(LaundryOccupancy.Idle, QiekjLaundryMatcher.occupancyOf(10, "", 0L, 1_000L))
        assertEquals(
            LaundryOccupancy.InUse(1_206_000L, 5_000L, null, null),
            QiekjLaundryMatcher.occupancyOf(20, "20分钟", 5_000L, 1_000L)
        )
        assertEquals(
            LaundryOccupancy.Unknown("使用中"),
            QiekjLaundryMatcher.occupancyOf(20, "", 0L, 1_000L)
        )
        assertEquals(
            LaundryOccupancy.Unknown("暂不可用"),
            QiekjLaundryMatcher.occupancyOf(30, "", 0L, 1_000L)
        )
    }

    @Test
    fun `洗鞋机整店机器按楼栋分组`() {
        val names = listOf(
            "205幢084号", "401幢021号", "403幢053号", "402幢010号", "300幢2楼408号",
            "306幢407号", "外招南3楼412号", "208幢413号", "206幢410号", "300幢4楼", "209栋2楼洗鞋机"
        )
        assertEquals(
            QiekjLaundryMatcher.BuildingGroup("205", "205幢"),
            QiekjLaundryMatcher.buildingGroupOf("205幢084号")
        )
        assertEquals(
            QiekjLaundryMatcher.BuildingGroup("外招南", "外招南楼"),
            QiekjLaundryMatcher.buildingGroupOf("外招南3楼412号")
        )
        assertEquals("209", QiekjLaundryMatcher.buildingGroupOf("209栋2楼洗鞋机")?.key)
        assertNull(QiekjLaundryMatcher.buildingGroupOf("3楼412号"))
        // 无「幢/栋」的 3 位数字是机位号，不算楼栋
        assertNull(QiekjLaundryMatcher.buildingOf("697号1楼"))
        assertNull(QiekjLaundryMatcher.buildingOf("外招南3楼412号"))
        assertEquals("402", QiekjLaundryMatcher.buildingOf("402"))

        // 编号楼栋按号升序在前，中文楼名在后
        assertEquals(
            listOf("205", "206", "208", "209", "300", "306", "401", "402", "403", "外招南"),
            QiekjLaundryMatcher.buildingGroupsOf(names).map { it.key }
        )
        assertEquals("外招南楼", QiekjLaundryMatcher.buildingGroupsOf(names).last().label)
    }
}
