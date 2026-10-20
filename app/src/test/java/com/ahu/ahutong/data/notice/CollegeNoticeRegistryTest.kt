package com.ahu.ahutong.data.notice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CollegeNoticeRegistryTest {

    @Test
    fun `current department matches the correct college`() {
        assertEquals("dy", CollegeNoticeRegistry.resolve("电子信息工程学院")?.key)
        assertEquals("cs", CollegeNoticeRegistry.resolve("计算机科学与技术学院")?.key)
        assertEquals("hx", CollegeNoticeRegistry.resolve("历史系")?.key)
        assertEquals("sfs", CollegeNoticeRegistry.resolve("安徽大学外国语学院")?.key)
        assertEquals("sbiahu", CollegeNoticeRegistry.resolve("安徽大学纽约石溪学院")?.key)
    }

    @Test
    fun `material college and material division stay distinct`() {
        assertEquals("mse", CollegeNoticeRegistry.resolve("材料科学与工程学院")?.key)
        assertEquals("fmse", CollegeNoticeRegistry.resolve("材料科学与工程学部")?.key)
    }

    @Test
    fun `department with whitespace and parentheses normalizes`() {
        assertEquals("dy", CollegeNoticeRegistry.resolve(" 电子信息 工程学院（主修） ")?.key)
        assertNull(CollegeNoticeRegistry.resolve("不存在学院"))
        assertNull(CollegeNoticeRegistry.resolve(null))
    }

    @Test
    fun `all configs use unique source keys and hosts`() {
        val configs = listOf(
            "材料科学与工程学部", "数学科学学院", "物理学院", "光电科学与工程学院", "化学化工学院",
            "材料科学与工程学院", "计算机科学与技术学院", "互联网学院", "电子信息工程学院",
            "电气工程与自动化学院", "人工智能学院", "机器人学院", "集成电路学院", "大数据与统计学院",
            "生命科学与医学工程学院", "资源与环境工程学院", "马克思主义学院", "文学院", "历史学院",
            "哲学学院", "新闻传播学院", "经济学院", "商学院", "外语学院", "法学院", "管理学院",
            "社会与政治学院", "艺术学院", "文典学院", "纽约石溪学院", "中法工程师学院",
            "体育军事教学部", "大学数学教学中心", "大学外语教学中心", "国际教育学院", "继续教育学院"
        ).mapNotNull(CollegeNoticeRegistry::resolve)

        assertEquals(36, configs.size)
        assertEquals(36, configs.map { it.key }.distinct().size)
        assertEquals(36, configs.map { it.host }.distinct().size)
    }
}
