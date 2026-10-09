package com.ahu.ahutong.data.notice

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CampusNoticeCacheContractTest {
    private val gson = Gson()

    @Test
    fun `empty notice cache retains typed source errors used by the error card`() {
        val json = """
            {
              "accountId": "test-account",
              "notices": [],
              "sourceStatuses": {
                "university": {
                  "sourceId": "university",
                  "lastError": "访问受限（HTTP 412）"
                },
                "postgraduate": {
                  "sourceId": "postgraduate",
                  "lastSuccessfulSyncDate": "2026-10-09",
                  "lastSuccessfulSyncAtMillis": 1791504000000
                }
              },
              "notificationsEnabled": true
            }
        """.trimIndent()
        val restored = gson.fromJson(json, CampusNoticeSnapshot::class.java)
        assertTrue(restored.notices.isEmpty())
        assertTrue(restored.sourceStatuses.values.any { it.lastError != null })
        assertEquals("访问受限（HTTP 412）", restored.sourceStatuses.getValue("university").lastError)
        assertEquals("2026-10-09", restored.sourceStatuses.getValue("postgraduate").lastSuccessfulSyncDate)
        assertEquals(1791504000000L, restored.sourceStatuses.getValue("postgraduate").lastSuccessfulSyncAtMillis)
        assertTrue(restored.notificationsEnabled)
    }

    @Test
    fun `persisted cache roundtrip retains articles statuses and stable field names`() {
        val original = CampusNoticeSnapshot(
            accountId = "test-account",
            notices = listOf(
                CampusNotice("university", "1", "通知一", "2026-10-09",
                    "https://www.ahu.edu.cn/2026/1009/c15046a1/page.htm", 1791504000000L, false),
                CampusNotice("university", "2", "通知二", "2026-10-08",
                    "https://www.ahu.edu.cn/2026/1008/c15046a2/page.htm", 1791417600000L, true)
            ),
            sourceStatuses = mapOf("university" to CampusNoticeSourceStatus(
                "university", "2026-10-09", 1791504000000L, "更新失败，请稍后重试")),
            notificationsEnabled = true
        )
        val json = gson.toJson(original)
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals("test-account", root.get("accountId").asString)
        assertTrue(root.getAsJsonObject("sourceStatuses").getAsJsonObject("university").has("lastError"))
        val restored = gson.fromJson(json, CampusNoticeSnapshot::class.java)
        assertEquals(original, restored)
        assertEquals(1, restored.unreadCount)
        assertFalse(restored.notices.first().read)
        assertEquals(original.sourceStatuses, restored.sourceStatuses)
    }
}
