package com.ahu.ahutong.data.crawler.api.one

import com.ahu.ahutong.data.model.DormitoryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * getQinshiInfo 响应解析容错测试。
 * 样本来自真机 HAR 抓包（2026-11-23，磬苑校区，凭证已脱敏）。
 */
class QinshiInfoParseTest {

    private val realSample = """
        {
          "XQ": "磬苑校区",
          "XQH": "5",
          "NAME": "榴园2号楼",
          "JZWH": "261",
          "SSFJH": "2439",
          "CWH": "D",
          "SFZS": "1"
        }
    """.trimIndent()

    @Test
    fun `parses real HAR sample with all fields`() {
        val info = QinshiInfoApi.parse(realSample)!!
        assertEquals("磬苑校区", info.campus)
        assertEquals("榴园2号楼", info.building)
        assertEquals("2439", info.room)
        assertTrue(info.resident)
    }

    @Test
    fun `resident false when SFZS is not 1`() {
        val variant = realSample.replace("\"SFZS\": \"1\"", "\"SFZS\": \"0\"")
        val info = QinshiInfoApi.parse(variant)!!
        assertEquals(false, info.resident)
        assertEquals("榴园2号楼", info.building)
    }

    @Test
    fun `missing optional fields still parses`() {
        val minimal = """{"NAME": "榴园2号楼"}"""
        val info = QinshiInfoApi.parse(minimal)!!
        assertEquals("榴园2号楼", info.building)
        assertEquals("", info.campus)
        assertEquals("", info.room)
        assertEquals(false, info.resident)
    }

    @Test
    fun `returns null on non-json body`() {
        assertNull(QinshiInfoApi.parse("<html><body>CAS login</body></html>"))
    }

    @Test
    fun `returns null when NAME blank`() {
        assertNull(QinshiInfoApi.parse("""{"XQ": "磬苑校区", "NAME": ""}"""))
    }

    @Test
    fun `returns null on error-shaped payload`() {
        // TP 框架错误回包：平铺 JSON 但无 NAME 字段。
        assertNull(QinshiInfoApi.parse("""{"status": "error", "message": "session timeout"}"""))
    }

    @Test
    fun `handles extra wrapper fields without breaking`() {
        val noisy = """
            {"some_future_wrapper": true, "XQ": "磬苑校区", "NAME": "榴园2号楼",
             "SSFJH": "2439", "SFZS": "1", "extra": [1, 2, 3]}
        """.trimIndent()
        val info = QinshiInfoApi.parse(noisy)!!
        assertEquals("榴园2号楼", info.building)
        assertEquals("2439", info.room)
    }
}
