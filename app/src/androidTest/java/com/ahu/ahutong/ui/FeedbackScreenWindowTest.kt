package com.ahu.ahutong.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.core.common.diagnostics.DiagnosticCategory
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.screen.settings.FeedbackScreen
import com.ahu.ahutong.ui.screen.settings.FeedbackHubScreen
import com.ahu.ahutong.ui.screen.settings.FeedbackExportScreen
import com.ahu.ahutong.ui.screen.settings.FeedbackUiState
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual themed controls with synthetic callbacks; never sends feedback. */
@RunWith(AndroidJUnit4::class)
class FeedbackScreenWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 60_000)
    fun encryptedExportAndShareUseSelectedLogsWithoutRequiringDescription() {
        val exported = AtomicReference<Set<DiagnosticCategory>>()
        val shared = AtomicReference<Set<DiagnosticCategory>>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    AHUTheme(AhuThemeConfig(
                        appUiTheme = AppUiTheme.entries.first(),
                        themeColorHex = null,
                        themeMode = AppThemeMode.FOLLOW_SYSTEM
                    )) {
                        var state by remember { mutableStateOf(FeedbackUiState()) }
                        FeedbackExportScreen(
                            state = state,
                            onCategoryChange = { category, selected ->
                                state = state.copy(selectedCategories =
                                    if (selected) state.selectedCategories + category
                                    else state.selectedCategories - category)
                            },
                            onExport = { exported.set(state.selectedCategories) },
                            onShare = { shared.set(state.selectedCategories) },
                            onBack = {}
                        )
                    }
                }
            }
            click("网络详情")
            assertNoVisible("问题描述")
            assertNoVisible("发送反馈")
            click("导出日志")
            click("分享日志")
            assertEquals(DiagnosticCategory.entries.toSet(), exported.get())
            assertEquals("Share must use exactly the same selections", exported.get(), shared.get())
        }
    }

    @Test(timeout = 120_000)
    fun sensitiveLogsRequireSelectionAndPreviewAndSubmissionWorkAcrossThemes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            AppUiTheme.entries.forEach { theme ->
                val previews = AtomicInteger()
                val submissions = AtomicInteger()
                scenario.onActivity { activity ->
                    activity.setContent {
                        AHUTheme(AhuThemeConfig(
                            appUiTheme = theme,
                            themeColorHex = null,
                            themeMode = AppThemeMode.FOLLOW_SYSTEM
                        )) {
                            var state by remember(theme) {
                                mutableStateOf(FeedbackUiState(description = "合成反馈：打开页面后加载失败"))
                            }
                            var route by remember(theme) { mutableStateOf("hub") }
                            val changeCategory: (DiagnosticCategory, Boolean) -> Unit = { category, selected ->
                                state = state.copy(selectedCategories =
                                    if (selected) state.selectedCategories + category
                                    else state.selectedCategories - category)
                            }
                            when (route) {
                                "hub" -> FeedbackHubScreen(
                                    onFeedback = { route = "feedback" },
                                    onExport = { route = "export" },
                                    onOpenQq = {}, onBack = {})
                                "export" -> FeedbackExportScreen(state, changeCategory,
                                    onExport = {}, onShare = {}, onBack = { route = "hub" })
                                else -> FeedbackScreen(
                                    state = state,
                                    onDescriptionChange = { state = state.copy(description = it) },
                                    onCategoryChange = changeCategory,
                                    onPreview = {
                                        previews.incrementAndGet()
                                        state = state.copy(preview = "合成反馈预览，不含真实凭据")
                                    },
                                    onDismissPreview = { state = state.copy(preview = null) },
                                    onSubmit = {
                                        submissions.incrementAndGet()
                                        state = state.copy(submitting = true)
                                    },
                                    onBack = { route = "hub" }
                                )
                            }
                        }
                    }
                }
                awaitNode("意见反馈").recycleNode()
                captureScreenshot(theme, "hub")
                click("日志导出")
                awaitNode("导出日志").recycleNode()
                captureScreenshot(theme, "export")
                click("返回")
                click("问题反馈")
                awaitNode("预览反馈").recycleNode()
                captureScreenshot(theme, "form")
                assertNoVisible("发送反馈")
                assertChecked("运行记录", true, theme)
                assertChecked("错误信息", true, theme)
                assertChecked("网络详情", false, theme)
                click("网络详情")
                assertChecked("网络详情", true, theme)
                click("预览反馈")
                awaitNode("反馈预览").recycleNode()
                awaitNode("合成反馈预览，不含真实凭据").recycleNode()
                captureScreenshot(theme, "preview")
                click("关闭")
                assertEquals("Preview callback ($theme)", 1, previews.get())
                assertNoVisible("发送反馈")
                click("预览反馈")
                awaitNode("反馈预览").recycleNode()
                click("发送反馈")
                val sending = awaitNode("处理中")
                assertFalse("Submission remains clickable ($theme)", isEnabledClickTarget(sending))
                sending.recycleNode()
                assertEquals("Submission callback ($theme)", 1, submissions.get())
            }
        }
    }

    private fun assertNoVisible(label: String) {
        val deadline = SystemClock.uptimeMillis() + 3_000
        do {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                try {
                    val found = find(root) {
                        it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label)
                    }
                    try { if (found == null) return } finally { found?.recycleNode() }
                } finally { root.recycleNode() }
            }
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("'$label' must not be on this page")
    }

    private fun assertChecked(label: String, expected: Boolean, theme: AppUiTheme) {
        val deadline = SystemClock.uptimeMillis() + 3_000
        var actual: Boolean? = null
        do {
            val node = awaitNode(label)
            try {
                val checkbox = findAncestor(node) { it.isCheckable }
                try {
                    actual = checkbox?.isChecked
                    if (actual == expected) return
                } finally {
                    checkbox?.recycleNode()
                }
            } finally {
                node.recycleNode()
            }
            SystemClock.sleep(80)
        } while (SystemClock.uptimeMillis() < deadline)
        assertTrue("'$label' must be a checkbox ($theme)", actual != null)
        assertEquals("Selection for '$label' ($theme)", expected, actual)
    }

    private fun click(label: String) {
        val node = awaitNode(label)
        try {
            var candidate: AccessibilityNodeInfo? = findAncestor(node) { it.isCheckable && it.isClickable }
                ?: AccessibilityNodeInfo.obtain(node)
            try {
                while (candidate != null && !candidate.isClickable) {
                    val parent = candidate.parent
                    candidate.recycleNode()
                    candidate = parent
                }
                assertTrue("'$label' did not handle click",
                    candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            } finally {
                candidate?.recycleNode()
            }
        } finally {
            node.recycleNode()
        }
        instrumentation.waitForIdleSync()
    }

    private fun isEnabledClickTarget(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        try {
            while (candidate != null && !candidate.isClickable) {
                val parent = candidate.parent
                candidate.recycleNode()
                candidate = parent
            }
            return candidate?.isEnabled == true
        } finally {
            candidate?.recycleNode()
        }
    }

    private fun awaitNode(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var direction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                try {
                    find(root) {
                        it.isVisibleToUser &&
                            (it.contentDescription?.toString() == label || it.text?.toString() == label)
                    }?.let { return it }
                    val offscreen = find(root) {
                        it.contentDescription?.toString() == label || it.text?.toString() == label
                    }
                    val requestedPosition = try {
                        offscreen?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id) == true
                    } finally {
                        offscreen?.recycleNode()
                    }
                    val scrollable = find(root) { it.isScrollable && it.isVisibleToUser }
                    try {
                        if (!requestedPosition && scrollable != null) {
                            if (scrollable.actionList.none { it.id == direction } ||
                                !scrollable.performAction(direction)) {
                                direction = if (direction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                                scrollable.performAction(direction)
                            }
                        }
                    } finally {
                        scrollable?.recycleNode()
                    }
                } finally {
                    root.recycleNode()
                }
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("No visible '$label' in feedback UI")
    }

    private fun captureScreenshot(theme: AppUiTheme, section: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "Could not capture feedback theme $theme"
        }
        try {
            val target = File(instrumentation.targetContext.getExternalFilesDir(null),
                "feedback-theme-${theme.name.lowercase()}-$section.png")
            target.outputStream().use {
                assertTrue("Could not save feedback screenshot", screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun findAncestor(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
        while (candidate != null) {
            if (predicate(candidate)) return candidate
            val parent = candidate.parent
            candidate.recycleNode()
            candidate = parent
        }
        return null
    }

    private fun find(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                find(child, predicate)?.let { return it }
            } finally {
                child.recycleNode()
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.recycleNode() = recycle()
}
