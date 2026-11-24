package com.ahu.ahutong.data.recharge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ElectricityBuildingMatchTest {

    @Test
    fun `中文数字楼栋名能对上阿拉伯数字宿舍楼栋`() {
        val options = listOf("榴园一号楼", "榴园二号楼", "榴园三号楼")
        assertEquals("榴园二号楼", ElectricityBuildingMatch.match("榴园2号楼", options))
    }

    @Test
    fun `不同后缀与空白不影响匹配`() {
        val options = listOf("榴园二号楼", "梅园一号楼")
        assertEquals("榴园二号楼", ElectricityBuildingMatch.match(" 榴园2栋 ", options))
        assertEquals("梅园一号楼", ElectricityBuildingMatch.match("梅园1号楼", options))
    }

    @Test
    fun `十以上的楼栋编号按数值比较`() {
        val options = listOf("榴园十一号楼", "南七号楼")
        assertEquals("榴园十一号楼", ElectricityBuildingMatch.match("榴园11号楼", options))
        assertEquals("南七号楼", ElectricityBuildingMatch.match("南7号楼", options))
    }

    @Test
    fun `同号多写法视为多候选不猜测`() {
        val options = listOf("榴园2号楼", "榴园二号楼")
        assertNull(ElectricityBuildingMatch.match("榴园2号楼", options))
    }

    @Test
    fun `选项多带园区前缀时按楼栋编号兜底匹配`() {
        assertEquals(
            "磬苑榴园二号楼",
            ElectricityBuildingMatch.match("榴园2号楼", listOf("磬苑榴园二号楼", "梅园一号楼"))
        )
    }

    @Test
    fun `楼栋编号不同不因前缀相同而命中`() {
        assertNull(ElectricityBuildingMatch.match("榴园2号楼", listOf("榴园21号楼")))
    }

    @Test
    fun `没有住宿信息或没有对应楼栋时返回空`() {
        val options = listOf("榴园一号楼")
        assertNull(ElectricityBuildingMatch.match(null, options))
        assertNull(ElectricityBuildingMatch.match("", options))
        assertNull(ElectricityBuildingMatch.match("兰园2号楼", options))
    }
}
