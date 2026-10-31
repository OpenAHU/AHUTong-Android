package com.ahu.ahutong.ui

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.graphics.Rect
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.haptics.hapticClickable
import com.ahu.ahutong.ui.haptics.hapticSelectable
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real themed controls and vibrator-service requests on a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class AppHapticsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 120_000)
    fun appOptInDefaultsOffAndDisablingAlsoStopsPreviouslyCapturedFeedback() {
        val original = shell("settings get system haptic_feedback_enabled").trim()
        val actions = AtomicInteger()
        val legacyFeedback = AtomicReference<HapticFeedback>()
        try {
            shell("settings put system haptic_feedback_enabled 1")
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                for (theme in AppUiTheme.entries) {
                    val config = AhuThemeConfig(theme, null, AppThemeMode.FOLLOW_SYSTEM)
                    val enabled = mutableStateOf(config.hapticFeedbackEnabled)
                    legacyFeedback.set(null)
                    scenario.onActivity { activity ->
                        activity.setContent {
                            AHUTheme(config.copy(hapticFeedbackEnabled = enabled.value)) {
                                legacyFeedback.compareAndSet(null, LocalHapticFeedback.current)
                                Column {
                                    Text(if (enabled.value) "触感已开启" else "触感已关闭")
                                    AppButton(onClick = { actions.incrementAndGet() }) { Text("测试按钮") }
                                }
                            }
                        }
                    }
                    instrumentation.waitForIdleSync()
                    awaitLabel("触感已关闭")
                    val before = ownRecords(vibratorDump())
                    clickButton()
                    SystemClock.sleep(250)
                    assertEquals("App haptics must default off for $theme", before, ownRecords(vibratorDump()))

                    scenario.onActivity { enabled.value = true }
                    instrumentation.waitForIdleSync()
                    awaitLabel("触感已开启")
                    clickButton()
                    SystemClock.sleep(450)
                    val afterEnabled = ownRecords(vibratorDump())
                    assertTrue("Opt-in must enable feedback for $theme", afterEnabled.any { it !in before })

                    scenario.onActivity { enabled.value = false }
                    instrumentation.waitForIdleSync()
                    awaitLabel("触感已关闭")
                    clickButton()
                    scenario.onActivity {
                        legacyFeedback.get().performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    SystemClock.sleep(250)
                    assertTrue("Opt-out must stop current and captured feedback for $theme",
                        afterEnabled == ownRecords(vibratorDump()))
                }
            }
            assertEquals(AppUiTheme.entries.size * 3, actions.get())
        } finally {
            if (original == "0" || original == "1") shell("settings put system haptic_feedback_enabled $original")
            else shell("settings delete system haptic_feedback_enabled")
        }
    }

    @Test(timeout = 120_000)
    fun allThemesDispatchButtonFeedbackAndRespectTheSystemOptOut() {
        val original = shell("settings get system haptic_feedback_enabled").trim()
        val actions = AtomicInteger()
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                for (theme in AppUiTheme.entries) {
                    scenario.onActivity { activity ->
                        activity.setContent {
                            AHUTheme(AhuThemeConfig(theme, null, AppThemeMode.FOLLOW_SYSTEM, hapticFeedbackEnabled = true)) {
                                Column {
                                    Text("触觉测试：${theme.displayName}")
                                    AppButton(onClick = { actions.incrementAndGet() }) {
                                        Text("测试按钮")
                                    }
                                }
                            }
                        }
                    }
                    instrumentation.waitForIdleSync()
                    shell("settings put system haptic_feedback_enabled 1")
                    val before = ownRecords(vibratorDump())
                    clickButton()
                    SystemClock.sleep(450)
                    val enabled = vibratorDump()
                    val afterEnabled = ownRecords(enabled)
                    assertTrue("Enabled feedback must reach the vibrator service for $theme", afterEnabled.any { it !in before })
                    shell("settings put system haptic_feedback_enabled 0")
                    clickButton()
                    SystemClock.sleep(250)
                    val disabled = vibratorDump()
                    assertEquals("System opt-out must prevent new requests for $theme",
                        afterEnabled, ownRecords(disabled))
                    val directory = instrumentation.targetContext.getExternalFilesDir(null)
                    File(directory, "haptics-${theme.storageValue}-on.txt").writeText(enabled)
                    File(directory, "haptics-${theme.storageValue}-off.txt").writeText(disabled)
                }
            }
            assertEquals(AppUiTheme.entries.size * 2, actions.get())
        } finally {
            if (original == "0" || original == "1") shell("settings put system haptic_feedback_enabled $original")
            else shell("settings delete system haptic_feedback_enabled")
        }
    }

    @Test(timeout = 120_000)
    fun dialogFeedbackUsesItsOwnFocusedWindowAndSuppressesTheUnfocusedHost() {
        val original = shell("settings get system haptic_feedback_enabled").trim()
        val hostFeedback = AtomicReference<HapticFeedback>()
        try {
            shell("settings put system haptic_feedback_enabled 1")
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                for (theme in AppUiTheme.entries) {
                    scenario.onActivity { activity ->
                        activity.setContent {
                            AHUTheme(AhuThemeConfig(theme, null, AppThemeMode.FOLLOW_SYSTEM, hapticFeedbackEnabled = true)) {
                                hostFeedback.set(LocalHapticFeedback.current)
                                AppDialog(title = "窗口触觉测试", onDismiss = {}, content = {
                                    val dialogFeedback = LocalHapticFeedback.current
                                    // Raw Material controls intentionally exercise the existing LocalHapticFeedback path.
                                    androidx.compose.material3.Button(onClick = {
                                        dialogFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }) { Text("弹窗反馈") }
                                    androidx.compose.material3.Button(onClick = {
                                        hostFeedback.get().performHapticFeedback(HapticFeedbackType.LongPress)
                                    }) { Text("失焦宿主反馈") }
                                })
                            }
                        }
                    }
                    instrumentation.waitForIdleSync()
                    val before = ownRecords(vibratorDump())
                    clickButton("弹窗反馈")
                    SystemClock.sleep(450)
                    val focused = ownRecords(vibratorDump())
                    assertTrue("Dialog LocalHapticFeedback must reach its own window for $theme", focused.any { it !in before })
                    clickButton("失焦宿主反馈")
                    SystemClock.sleep(250)
                    assertEquals("Unfocused Activity feedback must remain silent for $theme",
                        focused, ownRecords(vibratorDump()))
                }
            }
        } finally {
            if (original == "0" || original == "1") shell("settings put system haptic_feedback_enabled $original")
            else shell("settings delete system haptic_feedback_enabled")
        }
    }

    @Test(timeout = 60_000)
    fun disabledAlreadySelectedAndCancelledGesturesDoNotVibrate() {
        val original = shell("settings get system haptic_feedback_enabled").trim()
        val clicks = AtomicInteger()
        val selections = AtomicInteger()
        try {
            shell("settings put system haptic_feedback_enabled 1")
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        AHUTheme(AhuThemeConfig(AppUiTheme.MATERIAL, null, AppThemeMode.FOLLOW_SYSTEM, hapticFeedbackEnabled = true)) {
                            Column {
                                Text("禁用操作", Modifier.size(100.dp).hapticClickable(enabled = false) { clicks.incrementAndGet() })
                                Text("当前选项", Modifier.size(100.dp).hapticSelectable(selected = true) { selections.incrementAndGet() })
                                Text("取消手势", Modifier.size(100.dp).hapticClickable { clicks.incrementAndGet() })
                            }
                        }
                    }
                }
                instrumentation.waitForIdleSync()
                val before = ownRecords(vibratorDump())
                findActionNode("禁用操作", requireClickable = false).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clickButton("当前选项")
                val bounds = Rect()
                findActionNode("取消手势").getBoundsInScreen(bounds)
                val downTime = SystemClock.uptimeMillis()
                MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
                    bounds.exactCenterX(), bounds.exactCenterY(), 0).also {
                    instrumentation.sendPointerSync(it); it.recycle()
                }
                MotionEvent.obtain(downTime, downTime + 30, MotionEvent.ACTION_CANCEL,
                    bounds.exactCenterX(), bounds.exactCenterY(), 0).also {
                    instrumentation.sendPointerSync(it); it.recycle()
                }
                SystemClock.sleep(250)
                assertEquals(0, clicks.get())
                assertEquals(1, selections.get())
                assertEquals(before, ownRecords(vibratorDump()))
            }
        } finally {
            if (original == "0" || original == "1") shell("settings put system haptic_feedback_enabled $original")
            else shell("settings delete system haptic_feedback_enabled")
        }
    }

    private fun findActionNode(text: String, requireClickable: Boolean = true): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var node = currentLabel(text)
            while (requireClickable && node != null && !node.isClickable) node = node.parent
            if (node != null) return node
            SystemClock.sleep(100)
        }
        throw AssertionError("No action node for $text")
    }

    private fun awaitLabel(text: String) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (currentLabel(text) != null) return
            SystemClock.sleep(50)
        }
        throw AssertionError("The haptic preference must finish recomposing: $text")
    }

    private fun clickButton(labelText: String = "测试按钮") {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var node = currentLabel(labelText)
            while (node != null && !node.isClickable) node = node.parent
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            SystemClock.sleep(100)
        }
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "haptics-ui-failure.png")
        instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
            output.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        }
        throw AssertionError("The themed test button '$labelText' was not clickable; screenshot: $output")
    }

    // Compose exports virtual descendants; framework find-by-text does not search them reliably.
    @Suppress("DEPRECATION")
    private fun currentLabel(text: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) {
                return AccessibilityNodeInfo.obtain(node)
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try { find(child)?.let { return it } } finally { child.recycle() }
            }
            return null
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        return try { find(root) } finally { root.recycle() }
    }

    private fun ownRecords(dump: String): Set<String> = dump.lineSequence().filter {
        val packageName = instrumentation.targetContext.packageName
        it.contains("opPkg=$packageName") || it.contains("| $packageName (uid=")
    }.toSet()

    private fun vibratorDump(): String = shell("dumpsys vibrator_manager")

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
}
