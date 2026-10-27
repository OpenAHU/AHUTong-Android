package com.ahu.ahutong.ui

import android.os.SystemClock
import android.os.Build
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.screen.NormalizePreferencesParent
import com.ahu.ahutong.ui.screen.settings.Preferences
import com.ahu.ahutong.ui.screen.settings.ThemeLab
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import com.ahu.ahutong.utils.animatedComposable
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the production parent-normalization effect, navigation transitions and settings screens. */
@RunWith(AndroidJUnit4::class)
class SettingsReturnNavigationWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private data class Destination(val route: String?, val id: String, val parentId: String?)

    @Test(timeout = 120_000)
    fun leavingDetailsRestoresTheSamePreferencesEntryAcrossThemesAndBackActions() {
        val trace = StringBuilder("theme,route,entry,parent\n")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                AppUiTheme.entries.forEach { theme ->
                    val observed = AtomicReference<Destination>()
                    val marker = "settings-return-${theme.name}"
                    scenario.onActivity { activity ->
                        activity.setContent {
                            key(theme) {
                                AHUTheme(AhuThemeConfig(appUiTheme = theme, themeColorHex = null,
                                    themeMode = AppThemeMode.FOLLOW_SYSTEM)) {
                                    val nav = rememberNavController()
                                    val entry by nav.currentBackStackEntryAsState()
                                    LaunchedEffect(entry?.id) {
                                        entry?.let {
                                            val destination = Destination(it.destination.route, it.id,
                                                nav.previousBackStackEntry?.id)
                                            observed.set(destination)
                                            synchronized(trace) {
                                                trace.appendLine("${theme.name},${destination.route},${destination.id},${destination.parentId}")
                                            }
                                        }
                                    }
                                    NormalizePreferencesParent(nav, entry?.destination?.route, theme)
                                    Box(Modifier.fillMaxSize().semantics { contentDescription = marker }) {
                                        NavHost(nav, startDestination = "home") {
                                            animatedComposable("home") {
                                                AppButton(onClick = { nav.navigate("settings") },
                                                    modifier = Modifier.padding(top = 48.dp)) { Text("打开测试设置") }
                                            }
                                            animatedComposable("settings") {
                                                AppButton(onClick = { nav.navigate("preferences") },
                                                    modifier = Modifier.padding(top = 48.dp)) { Text("打开测试偏好设置") }
                                            }
                                            animatedComposable("preferences") {
                                                Preferences(onBack = { nav.popBackStack() },
                                                    onOpenThemeLab = { nav.navigate("settings__theme_lab") },
                                                    undergraduateEnabled = true)
                                            }
                                            animatedComposable("settings__theme_lab") {
                                                ThemeLab(onBack = { nav.popBackStack() })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    awaitNode { it.contentDescription?.toString() == marker }.recycleNode()
                    click("打开测试设置")
                    click("打开测试偏好设置")
                    awaitRoute(observed, "preferences")
                    settle()
                    val original = observed.get()
                    assertNotNull(original)
                    listOf(true, false).forEach { systemBack ->
                        click("详细设定")
                        awaitRoute(observed, "settings__theme_lab")
                        settle()
                        assertEquals("Details must retain the original parent entry", original.id, observed.get().parentId)
                        if (systemBack) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK) else click("返回")
                        awaitRoute(observed, "preferences")
                        settle()
                        assertEquals("Returning from details recreated preferences ($theme, systemBack=$systemBack)",
                            original.id, observed.get().id)
                        assertEquals(original.parentId, observed.get().parentId)
                        awaitNode { it.text?.toString() == "偏好设置" }.recycleNode()
                    }
                }
            }
        } finally {
            File(instrumentation.targetContext.getExternalFilesDir(null), "settings-return-navigation.csv")
                .writeText(synchronized(trace) { trace.toString() })
        }
    }

    private fun settle() {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(400, 4_000)
    }

    private fun awaitRoute(observed: AtomicReference<Destination>, expected: String) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (observed.get()?.route == expected) return
            SystemClock.sleep(40)
        }
        throw AssertionError("Expected $expected, observed ${observed.get()}")
    }

    private fun click(label: String) {
        val matches: (AccessibilityNodeInfo) -> Boolean = {
            (it.text?.toString() == label || it.contentDescription?.toString() == label) &&
                (label == "返回" || hasClickableAncestor(it))
        }
        val initial = awaitNode(matches)
        try { initial.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id) }
        finally { initial.recycleNode() }
        settle()
        val node = awaitNode(matches)
        val bounds = Rect().also(node::getBoundsInScreen)
        var target: AccessibilityNodeInfo? = node
        try {
            while (target != null && !target.isClickable) {
                val parent = target.parent
                target.recycleNode()
                target = parent
            }
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
        } finally { target?.recycleNode() }
        val now = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(40)
        }
    }

    private fun hasClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        try {
            while (candidate != null) {
                if (candidate.isClickable && candidate.isEnabled) return true
                val parent = candidate.parent
                candidate.recycleNode()
                candidate = parent
            }
            return false
        } finally { candidate?.recycleNode() }
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var direction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        while (SystemClock.uptimeMillis() < deadline) {
            if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                try {
                    find(root) { it.isVisibleToUser && predicate(it) }?.let { return it }
                    val scroll = find(root) { it.isVisibleToUser && it.isScrollable }
                    try {
                        if (scroll != null && !scroll.performAction(direction)) {
                            direction = if (direction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                            scroll.performAction(direction)
                        }
                    } finally { scroll?.recycleNode() }
                } finally { root.recycleNode() }
            }
            SystemClock.sleep(40)
        }
        throw AssertionError("No visible settings navigation control")
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
