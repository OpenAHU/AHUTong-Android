package com.ahu.ahutong.ui.screen

import com.ahu.ahutong.data.model.AppUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainNavigationPolicyTest {
    @Test
    fun `all themes keep settings as the preferences parent`() {
        AppUiTheme.entries.forEach { theme ->
            assertEquals("settings", settingsParentRoute(theme), theme.name)
        }
    }

    @Test
    fun `returning from theme details must not replace an existing preferences entry`() {
        AppUiTheme.entries.forEach { theme ->
            assertFalse(shouldNormalizePreferencesParent(theme, "settings"), theme.name)
            assertTrue(shouldNormalizePreferencesParent(theme, "home"), theme.name)
        }
    }
}
