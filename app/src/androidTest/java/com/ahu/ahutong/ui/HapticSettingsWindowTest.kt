package com.ahu.ahutong.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.screen.settings.ThemeSettingsSection
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real settings section and DataStore; restores only the preference touched by this test. */
@RunWith(AndroidJUnit4::class)
class HapticSettingsWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test(timeout = 90_000)
    fun themeSwitchPersistsAcrossActivityRecreationAndCanBeDisabledAgain() = runBlocking<Unit> {
        val settings = PreferencesManager(context)
        val original = settings.hapticFeedbackEnabled.first()
        try {
            settings.setHapticFeedbackEnabled(false)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun showThemeSection() {
                    scenario.onActivity { activity ->
                        activity.setContent {
                            val viewModel: PreferencesViewModel = hiltViewModel()
                            val theme by viewModel.appUiTheme.collectAsState()
                            val color by viewModel.themeColor.collectAsState()
                            val mode by viewModel.appThemeMode.collectAsState()
                            val enabled by viewModel.hapticFeedbackEnabled.collectAsState()
                            AHUTheme(AhuThemeConfig(theme, color, mode, hapticFeedbackEnabled = enabled)) {
                                SettingsBackdropContainer { backdrop ->
                                    SettingsPageLayout(title = "偏好设置", onBack = {}) {
                                        ThemeSettingsSection(viewModel, backdrop, onOpenDetails = {}, onOpenThemeColors = {})
                                    }
                                }
                            }
                        }
                    }
                    instrumentation.waitForIdleSync()
                }
                showThemeSection()
                awaitSwitchState(false)
                assertFalse(settings.hapticFeedbackEnabled.first())
                clickSwitch()
                awaitStored(true)
                assertTrue(PreferencesManager(context).hapticFeedbackEnabled.first())
                assertTrue(settings.getStartupThemePreferences()?.hapticFeedbackEnabled == true)

                scenario.recreate()
                showThemeSection()
                awaitSwitchState(true)
                assertTrue(PreferencesManager(context).hapticFeedbackEnabled.first())
                clickSwitch()
                awaitStored(false)
                awaitSwitchState(false)
                assertEquals(false, settings.getStartupThemePreferences()?.hapticFeedbackEnabled)
                SystemClock.sleep(300) // Let the switch's visual transition settle before capturing it.
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(context.getExternalFilesDir(null), "haptic-theme-setting.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } finally { settings.setHapticFeedbackEnabled(original) }
    }

    private suspend fun awaitStored(expected: Boolean) {
        val settings = PreferencesManager(context)
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while ((settings.hapticFeedbackEnabled.first() != expected ||
                settings.getStartupThemePreferences()?.hapticFeedbackEnabled != expected) &&
            SystemClock.elapsedRealtime() < deadline) {
            kotlinx.coroutines.delay(50)
        }
        assertEquals(expected, settings.hapticFeedbackEnabled.first())
    }

    @Suppress("DEPRECATION")
    private fun switchNode(): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == "haptics 触感") return AccessibilityNodeInfo.obtain(node)
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try { find(child)?.let { return it } } finally { child.recycle() }
            }
            return null
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow
        return root?.let { try { find(it) } finally { it.recycle() } }
    }

    private fun awaitSwitchState(expected: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var node = switchNode()
            while (node != null && !node.isCheckable) node = node.parent
            if (node?.isChecked == expected) return
            SystemClock.sleep(50)
        }
        throw AssertionError("The beta haptics switch must show selected=$expected")
    }

    private fun clickSwitch() {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var node = switchNode()
            while (node != null && !node.isClickable) node = node.parent
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            SystemClock.sleep(100)
        }
        throw AssertionError("The beta haptics switch must be visible in the theme section")
    }
}
