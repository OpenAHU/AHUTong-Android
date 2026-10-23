package com.ahu.ahutong.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.core.common.UserNoticeHolder
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.error.UnexpectedErrorReport
import com.ahu.ahutong.error.UnexpectedErrors
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real Dialog window on all three themes without opening QQ or reporting anything. */
@RunWith(AndroidJUnit4::class)
class UnexpectedErrorDialogWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 90_000)
    fun realHostDefersBackgroundErrorSurvivesRecreationAndCopiesSanitizedTrace() {
        val marker = "HostBackgroundRegression"
        val reportIds = mutableListOf<Long>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.moveToState(Lifecycle.State.CREATED)
                val cause = NullPointerException("$marker missing field")
                cause.stackTrace = arrayOf(StackTraceElement("example.MailParser", "parse", "MailParser.kt", 42))
                val failure = IllegalStateException(
                    "$marker https://ahu.edu.cn/mail?sid=synthetic-session-secret&token=synthetic-token-secret\n" +
                        "{\"password\":\"synthetic-password-secret\"}", cause
                )
                assertTrue("Unexpected failure was not handled", UserNoticeHolder.reportUnexpected(failure))
                val report = UnexpectedErrors.queue.reports.value.single { it.details.contains(marker) }
                reportIds += report.id
                instrumentation.waitForIdleSync()
                assertNoVisibleDialogFor(500)
                assertTrue("Background report was consumed before showing",
                    UnexpectedErrors.queue.reports.value.any { it.id == report.id })

                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitVisibleBounds("出现意料外的错误，请联系开发者。以下为错误信息")
                scenario.recreate()
                awaitVisibleBounds("出现意料外的错误，请联系开发者。以下为错误信息")
                assertTrue("Activity recreation lost the pending report",
                    UnexpectedErrors.queue.reports.value.any { it.id == report.id })

                tap(awaitVisibleBounds("复制"))
                instrumentation.waitForIdleSync()
                val copied = AtomicReference<String?>()
                scenario.onActivity { activity ->
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    copied.set(clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                }
                val text = requireNotNull(copied.get()) { "The real copy action did not populate the clipboard" }
                assertTrue(text.contains(marker))
                assertTrue(text.contains("Caused by: java.lang.NullPointerException"))
                assertTrue(text.contains("MailParser.kt:42"))
                assertTrue(text.contains("AHUTong"))
                listOf("synthetic-session-secret", "synthetic-token-secret", "synthetic-password-secret")
                    .forEach { assertFalse("Copied credential: $it", text.contains(it)) }
                assertTrue("Expected redaction marker", text.contains("[已隐藏]"))
                assertTrue("Copy must keep the report available",
                    UnexpectedErrors.queue.reports.value.any { it.id == report.id })

                tap(awaitVisibleBounds("取消"))
                instrumentation.waitForIdleSync()
                assertFalse("Cancel did not consume the report",
                    UnexpectedErrors.queue.reports.value.any { it.id == report.id })
                assertNoVisibleDialogFor(300)
            } finally {
                // Only remove this test's synthetic report; preserve any unrelated queued failures.
                reportIds.forEach(UnexpectedErrors.queue::dismiss)
            }
        }
    }

    @Test(timeout = 90_000)
    fun diagnosticDialogKeepsAllThreeActionsVisibleAndClickableAcrossThemes() {
        val activity = AtomicReference<MainActivity?>(null)
        val ready = CountDownLatch(1)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { candidate, stage ->
            if (candidate is MainActivity && stage == Stage.RESUMED && activity.compareAndSet(null, candidate)) {
                ready.countDown()
            }
        }
        monitor.addLifecycleCallback(callback)
        try {
            val component = "${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}"
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("am start -n $component")
            ).bufferedReader().use { it.readText() }
            assertTrue("MainActivity did not resume", ready.await(20, TimeUnit.SECONDS))
            val host = requireNotNull(activity.get())
            AppUiTheme.entries.forEach { theme ->
                val copies = AtomicInteger()
                val feedback = AtomicInteger()
                instrumentation.runOnMainSync {
                    host.setContent {
                        AHUTheme(AhuThemeConfig(appUiTheme = theme, themeColorHex = null,
                            themeMode = AppThemeMode.FOLLOW_SYSTEM)) {
                            var visible by remember(theme) { mutableStateOf(true) }
                            Text("错误弹窗回归 $theme")
                            if (visible) UnexpectedErrorDialog(
                                report = UnexpectedErrorReport(1, "IllegalStateException: fixture\n" +
                                    (1..120).joinToString("\n") { "at example.Parser.parse(Parser.kt:$it)" }),
                                onDismiss = { visible = false },
                                onCopy = { copies.incrementAndGet() },
                                onFeedback = { feedback.incrementAndGet() }
                            )
                        }
                    }
                }
                awaitVisibleBounds("出现意料外的错误，请联系开发者。以下为错误信息")
                tap(awaitVisibleBounds("复制"))
                tap(awaitVisibleBounds("反馈"))
                assertEquals("Copy action ($theme)", 1, copies.get())
                assertEquals("Feedback action ($theme)", 1, feedback.get())
                tap(awaitVisibleBounds("取消"))
                awaitVisibleBounds("错误弹窗回归 $theme")
            }
        } finally {
            monitor.removeLifecycleCallback(callback)
            Handler(Looper.getMainLooper()).post { activity.get()?.finish() }
        }
    }

    private fun awaitVisibleBounds(text: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val found = visibleBounds(root, text)
                @Suppress("DEPRECATION")
                root.recycle()
                if (found != null) return found
            }
            SystemClock.sleep(80)
        }
        throw AssertionError("No visible '$text' in the active window")
    }

    private fun assertNoVisibleDialogFor(durationMillis: Long) {
        val deadline = SystemClock.uptimeMillis() + durationMillis
        do {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val visible = visibleBounds(root, "意外错误")
                @Suppress("DEPRECATION")
                root.recycle()
                assertTrue("An unexpected-error Dialog was visible while the Activity was stopped", visible == null)
            }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
    }

    private fun visibleBounds(node: AccessibilityNodeInfo, text: String): Rect? {
        if (node.isVisibleToUser && node.text?.toString() == text) {
            val bounds = Rect().also(node::getBoundsInScreen)
            if (!bounds.isEmpty) return bounds
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = visibleBounds(child, text)
            @Suppress("DEPRECATION")
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun tap(bounds: Rect) {
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            val event = MotionEvent.obtain(downTime, downTime + index * 50L, action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }
}
