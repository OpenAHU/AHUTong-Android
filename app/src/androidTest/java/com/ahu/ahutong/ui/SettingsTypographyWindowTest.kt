package com.ahu.ahutong.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsChoice
import com.ahu.ahutong.ui.components.SettingsInfoRow
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSelectRow
import com.ahu.ahutong.ui.components.SettingsToggleRow
import com.ahu.ahutong.ui.screen.settings.Preferences
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real settings rows must remain visible and operable at normal and enlarged font scales. */
@RunWith(AndroidJUnit4::class)
class SettingsTypographyWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val titles = listOf("常规设置甲", "常规设置乙", "常规设置丙", "常规设置丁")
    private val summaries = listOf("说明文字甲", "说明文字乙", "说明文字丙", "说明文字丁")

    @Test(timeout = 180_000)
    fun settingsRemainReadableAndInteractiveAcrossThemesAndFontScales() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            AppUiTheme.entries.forEach { theme ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        key(theme, "preferences") {
                            AHUTheme(AhuThemeConfig(appUiTheme = theme, themeColorHex = null,
                                themeMode = AppThemeMode.FOLLOW_SYSTEM)) {
                                Preferences(onBack = {}, onOpenThemeLab = {})
                            }
                        }
                    }
                }
                awaitText("偏好设置").recycleNode()
                awaitText("主题套装").recycleNode()
                captureScreenshot(theme, 1f, "preferences")
            }
            AppUiTheme.entries.forEach { theme ->
                listOf(1f, 1.3f).forEach { scale ->
                    val actions = AtomicInteger()
                    val toggles = AtomicInteger()
                    val selection = AtomicInteger()
                    val marker = "字体检查 ${theme.ordinal}-${(scale * 100).toInt()}"
                    scenario.onActivity { activity ->
                        activity.setContent {
                            key(theme, scale) {
                                val density = LocalDensity.current
                                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                                    AHUTheme(AhuThemeConfig(appUiTheme = theme, themeColorHex = null,
                                        themeMode = AppThemeMode.FOLLOW_SYSTEM)) {
                                        var toggled by remember(theme, scale) { mutableStateOf(false) }
                                        var selected by remember(theme, scale) { mutableStateOf(0) }
                                        SettingsBackdropContainer(Modifier.fillMaxSize()) { backdrop ->
                                            SettingsPageLayout(title = marker, backdrop = backdrop,
                                                bottomPadding = 24.dp) {
                                                AppSectionCard(modifier = Modifier.padding(horizontal = 16.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                                    SettingsActionRow(title = titles[0], subtitle = summaries[0],
                                                        value = "状态甲", onClick = { actions.incrementAndGet() })
                                                    SettingsInfoRow(title = titles[1], subtitle = summaries[1], value = "状态乙")
                                                    SettingsToggleRow(title = titles[2], subtitle = summaries[2],
                                                        selected = toggled, backdrop = backdrop,
                                                        onSelectedChange = { toggled = it; toggles.incrementAndGet() })
                                                    SettingsSelectRow(title = titles[3], subtitle = summaries[3],
                                                        selected = selected,
                                                        choices = listOf(SettingsChoice(0, "默认值"), SettingsChoice(1, "备选值")),
                                                        onSelected = { selected = it; selection.set(it) })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    awaitText(marker).recycleNode()
                    awaitText(titles.last()).recycleNode()
                    instrumentation.waitForIdleSync()
                    instrumentation.uiAutomation.waitForIdle(300, 3_000)
                    // Character-location extras are not reliable for all Compose accessibility
                    // nodes. Verify visible bounds and interaction; inspect role styling in PNGs.
                    (titles + summaries + listOf("状态甲", "状态乙", "默认值")).forEach {
                        assertReadable(it, theme, scale)
                    }

                    click(titles[0])
                    awaitValue("action callback") { actions.get() == 1 }
                    click(titles[2])
                    awaitValue("toggle callback") { toggles.get() == 1 }
                    click("默认值")
                    click("备选值")
                    awaitValue("selection callback") { selection.get() == 1 }
                    assertEquals("Selecting an option must not toggle the switch", 1, toggles.get())
                    captureScreenshot(theme, scale)
                }
            }
        }
    }

    private fun assertReadable(label: String, theme: AppUiTheme, scale: Float) {
        val node = awaitText(label)
        val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
        try {
            val bounds = Rect().also(node::getBoundsInScreen)
            val screen = Rect().also(root::getBoundsInScreen)
            assertTrue("Empty text bounds for '$label' ($theme, $scale)", !bounds.isEmpty)
            assertTrue("Text lies outside the viewport for '$label' ($theme, $scale): $bounds / $screen",
                screen.contains(bounds))
        } finally { root.recycleNode(); node.recycleNode() }
    }

    private fun click(label: String) {
        val node = awaitText(label)
        try {
            var target: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
            try {
                while (target != null && !target.isClickable) {
                    val parent = target.parent
                    target.recycleNode()
                    target = parent
                }
                assertTrue("'$label' must handle selection", target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            } finally { target?.recycleNode() }
        } finally { node.recycleNode() }
    }

    private fun awaitText(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var direction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                try {
                    find(root) { it.isVisibleToUser && it.text?.toString() == label }?.let { return it }
                    find(root) { it.isVisibleToUser && it.text?.contains(label) == true }?.let { return it }
                    val scroll = find(root) { it.isVisibleToUser && it.isScrollable }
                    try {
                        if (scroll != null && (scroll.actionList.none { it.id == direction } || !scroll.performAction(direction))) {
                            direction = if (direction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                            scroll.performAction(direction)
                        }
                    }
                    finally { scroll?.recycleNode() }
                } finally { root.recycleNode() }
            }
            SystemClock.sleep(80)
        }
        throw AssertionError("No visible settings text '$label'")
    }

    private fun awaitValue(label: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 3_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(80)
        }
        throw AssertionError("Missing $label")
    }

    private fun captureScreenshot(theme: AppUiTheme, scale: Float, page: String? = null) {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(300, 3_000)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val filename = if (page == null)
                "settings-typography-${theme.name.lowercase()}-${(scale * 100).toInt()}.png"
            else "settings-$page-${theme.name.lowercase()}.png"
            File(instrumentation.targetContext.getExternalFilesDir(null), filename)
                .outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try { find(child, predicate)?.let { return it } } finally { child.recycleNode() }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.recycleNode() = recycle()
}
