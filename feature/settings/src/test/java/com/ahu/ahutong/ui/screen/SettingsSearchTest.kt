package com.ahu.ahutong.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val entries = listOf(
        SettingsSearchEntry("外观与主题", listOf("深色", "夜间"), open = {}),
        SettingsSearchEntry("通知与提醒", listOf("推送", "电费", "预警"), open = {}),
        SettingsSearchEntry("插件与安装", listOf("扩展", "ahup"), open = {})
    )

    @Test fun matchesTheTitle() {
        assertEquals(listOf("插件与安装"), searchSettings(entries, "插件").map { it.title })
    }

    @Test fun matchesSynonymsFromTheKeywordList() {
        assertEquals(listOf("外观与主题"), searchSettings(entries, "夜间").map { it.title })
        assertEquals(listOf("通知与提醒"), searchSettings(entries, "电费").map { it.title })
    }

    @Test fun blankQueryReturnsNothing() {
        assertTrue(searchSettings(entries, "   ").isEmpty())
    }

    @Test fun unknownQueryReturnsNothing() {
        assertTrue(searchSettings(entries, "不存在的设置").isEmpty())
    }
}
