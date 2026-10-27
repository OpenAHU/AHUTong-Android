package com.ahu.ahutong.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.core.common.diagnostics.DiagnosticCategory
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.screen.settings.FeedbackExportScreen
import com.ahu.ahutong.ui.screen.settings.FeedbackScreen
import com.ahu.ahutong.ui.screen.settings.FeedbackUiState
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks actual row/action spacing and multiline editing without submitting any feedback. */
@OptIn(ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class FeedbackSpacingWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 180_000)
    fun feedbackAndExportKeepSensitiveRowsAndActionsSpacedAcrossThemesAndFontScales() {
        val service = instrumentation.uiAutomation.serviceInfo
        val originalFlags = service.flags
        service.flags = originalFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        instrumentation.uiAutomation.serviceInfo = service
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                AppUiTheme.entries.forEach { theme ->
                    listOf(1f, 1.3f).forEach { scale ->
                        listOf("form", "export").forEach { page ->
                            val marker = "feedback-spacing-${theme.name}-${(scale * 100).toInt()}-$page"
                            val description = AtomicReference("")
                            val selected = AtomicReference(setOf(DiagnosticCategory.RUNTIME, DiagnosticCategory.ERROR))
                            scenario.onActivity { activity ->
                                activity.setContent {
                                    key(theme, scale, page) {
                                        val density = LocalDensity.current
                                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                                            AHUTheme(AhuThemeConfig(appUiTheme = theme, themeColorHex = null,
                                                themeMode = AppThemeMode.FOLLOW_SYSTEM)) {
                                                var state by remember { mutableStateOf(FeedbackUiState()) }
                                                val select: (DiagnosticCategory, Boolean) -> Unit = { category, checked ->
                                                    val categories = if (checked) state.selectedCategories + category
                                                        else state.selectedCategories - category
                                                    state = state.copy(selectedCategories = categories)
                                                    selected.set(categories)
                                                }
                                                Box(Modifier.fillMaxSize().semantics {
                                                    contentDescription = marker
                                                    testTagsAsResourceId = true
                                                }) {
                                                    if (page == "form") FeedbackScreen(
                                                        state = state,
                                                        onDescriptionChange = {
                                                            state = state.copy(description = it)
                                                            description.set(it)
                                                        },
                                                        onCategoryChange = select,
                                                        onPreview = { state = state.copy(preview = "合成反馈预览") },
                                                        onDismissPreview = { state = state.copy(preview = null) },
                                                        onSubmit = { throw AssertionError("Spacing fixture must not submit feedback") },
                                                        onBack = {}
                                                    ) else FeedbackExportScreen(state, select,
                                                        onExport = {}, onShare = {}, onBack = {})
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            awaitNode { it.contentDescription?.toString() == marker }.recycleNode()
                            instrumentation.waitForIdleSync()
                            instrumentation.uiAutomation.waitForIdle(300, 3_000)
                            val primaryTag = if (page == "form") "feedback_preview" else "feedback_export"
                            val initialAction = awaitNode { it.viewIdResourceName == primaryTag }
                            try {
                                initialAction.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                            } finally { initialAction.recycleNode() }
                            instrumentation.uiAutomation.waitForIdle(300, 3_000)
                            val network = networkRow()
                            val action = awaitNode {
                                it.viewIdResourceName == primaryTag
                            }
                            try {
                                val rowBounds = Rect().also(network::getBoundsInScreen)
                                val actionBounds = Rect().also(action::getBoundsInScreen)
                                val density = instrumentation.targetContext.resources.displayMetrics.density
                                assertFalse("Network details must remain opt-in", network.isChecked)
                                assertTrue("Sensitive row is too short ($theme, $scale, $page): $rowBounds",
                                    rowBounds.height() + 1 >= 80f * density)
                                assertTrue("Log group crowds the primary action ($theme, $scale, $page): $rowBounds / $actionBounds",
                                    actionBounds.top - rowBounds.bottom + 1 >= 24f * density)
                                assertInsideViewport("network row", rowBounds)
                                assertInsideViewport("primary action", actionBounds)
                                captureScreenshot(theme, scale, page, "empty")
                                assertTrue("Sensitive choice did not respond", network.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                            } finally { network.recycleNode(); action.recycleNode() }
                            awaitValue { DiagnosticCategory.NETWORK in selected.get() }
                            awaitNetworkChecked()

                            if (page == "form") {
                                val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                                try {
                                    val send = find(root) { it.isVisibleToUser && it.text?.toString() == "发送反馈" }
                                    try { assertTrue("Send belongs only to the preview dialog", send == null) }
                                    finally { send?.recycleNode() }
                                } finally { root.recycleNode() }
                                val editable = awaitNode { it.isEditable }
                                val multiline = "第一行：合成反馈\n第二行：复现步骤\n第三行：期望结果"
                                try {
                                    val arguments = Bundle().apply {
                                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, multiline)
                                    }
                                    assertTrue("Feedback field must accept multiline editing ($theme, $scale)",
                                        editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
                                } finally { editable.recycleNode() }
                                awaitValue { description.get() == multiline }
                                awaitNode { it.isEditable && it.text?.toString() == multiline }.recycleNode()
                                scenario.onActivity { activity ->
                                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                        .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                                }
                                awaitNode { it.isEditable && it.text?.toString() == multiline }.recycleNode()
                                awaitNetworkChecked()
                                awaitRenderedFrames(scenario)
                                captureScreenshot(theme, scale, page, "filled")
                            }
                        }
                    }
                }
            }
        } finally {
            service.flags = originalFlags
            instrumentation.uiAutomation.serviceInfo = service
        }
    }

    private fun networkRow(): AccessibilityNodeInfo {
        val label = awaitNode {
            it.contentDescription?.toString() == "网络详情" || it.text?.toString() == "网络详情"
        }
        var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(label)
        label.recycleNode()
        while (candidate != null && !candidate.isCheckable) {
            val parent = candidate.parent
            candidate.recycleNode()
            candidate = parent
        }
        return requireNotNull(candidate) { "Network details must expose the whole checkable row" }
    }

    private fun assertInsideViewport(label: String, bounds: Rect) {
        val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
        try {
            val viewport = Rect().also(root::getBoundsInScreen)
            assertTrue("$label is clipped: $bounds / $viewport", !bounds.isEmpty && viewport.contains(bounds))
        } finally { root.recycleNode() }
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var direction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                try {
                    find(root) { it.isVisibleToUser && predicate(it) }?.let { return it }
                    val scroll = find(root) { it.isVisibleToUser && it.isScrollable }
                    try {
                        if (scroll != null && (scroll.actionList.none { it.id == direction } || !scroll.performAction(direction))) {
                            direction = if (direction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                            scroll.performAction(direction)
                        }
                    } finally { scroll?.recycleNode() }
                } finally { root.recycleNode() }
            }
            SystemClock.sleep(80)
        }
        throw AssertionError("No visible feedback control matching the test fixture")
    }

    private fun awaitValue(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 3_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(80)
        }
        throw AssertionError("Feedback control callback was not applied")
    }

    private fun awaitNetworkChecked() {
        val deadline = SystemClock.uptimeMillis() + 3_000
        while (SystemClock.uptimeMillis() < deadline) {
            val row = networkRow()
            try { if (row.isChecked) return } finally { row.recycleNode() }
            SystemClock.sleep(80)
        }
        throw AssertionError("Network details did not become checked in the actual UI")
    }

    private fun awaitRenderedFrames(scenario: ActivityScenario<MainActivity>) {
        val frames = CountDownLatch(1)
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            view.postOnAnimation { view.postOnAnimation { frames.countDown() } }
        }
        assertTrue("Feedback fixture did not render updated frames", frames.await(3, TimeUnit.SECONDS))
    }

    private fun captureScreenshot(theme: AppUiTheme, scale: Float, page: String, state: String) {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(300, 3_000)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null),
                "feedback-spacing-${theme.name.lowercase()}-${(scale * 100).toInt()}-$page-$state.png")
                .outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { image.recycle() }
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
