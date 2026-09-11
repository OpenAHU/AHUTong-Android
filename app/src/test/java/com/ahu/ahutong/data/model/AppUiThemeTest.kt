package com.ahu.ahutong.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AppUiThemeTest {
    @Test
    fun `stored theme wins over legacy liquid glass preference`() {
        assertEquals(AppUiTheme.MIUIX, AppUiTheme.fromStorage("miuix", false))
        assertEquals(AppUiTheme.RADIANT, AppUiTheme.fromStorage("radiant_ui", false))
    }

    @Test
    fun `legacy preference migrates without changing appearance`() {
        assertEquals(AppUiTheme.MATERIAL, AppUiTheme.fromStorage(null, false))
        assertEquals(AppUiTheme.RADIANT, AppUiTheme.fromStorage(null, true))
        assertEquals(AppUiTheme.RADIANT, AppUiTheme.fromStorage(null, null))
    }
}
