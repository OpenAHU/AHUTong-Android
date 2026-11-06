package com.ahu.ahutong.ui

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.Vibrator
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.haptics.AppHapticEvent
import com.ahu.ahutong.ui.haptics.rememberAppHapticFeedback
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic controls only: never reads accounts or changes persistent app/system preferences. */
@RunWith(AndroidJUnit4::class)
class SemanticHapticsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val events = listOf(
        AppHapticEvent.Click,
        AppHapticEvent.ToggleOn, AppHapticEvent.ToggleOff,
        AppHapticEvent.Expand, AppHapticEvent.Collapse
    )

    @Test(timeout = 60_000)
    fun switchesAndPopupDispatchDistinctEffectsWhileClosingStaysQuiet() {
        val context = instrumentation.targetContext
        assumeTrue("System touch feedback is disabled; settings are left unchanged",
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0)
        assumeTrue("Device has no vibrator", context.getSystemService(Vibrator::class.java)?.hasVibrator() == true)
        val initialDump = vibratorDump()
        assumeTrue("Vibration history is not exposed on this device", initialDump.contains("Recent vibrations:"))
        val evidence = linkedMapOf<AppHapticEvent, Set<String>>()
        launchIsolatedTestActivity().use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    // Opt-in exists only in this composition; no settings-store mutation.
                    AHUTheme(AhuThemeConfig(AppUiTheme.MATERIAL, null,
                        AppThemeMode.FOLLOW_SYSTEM, hapticFeedbackEnabled = true)) {
                        val feedback = rememberAppHapticFeedback()
                        Column {
                            events.forEach { event ->
                                // The outer AppButton adds a generic click; the semantic event must win.
                                AppButton(onClick = { feedback.perform(event) }) {
                                    Text("触感合成 ${event.name}")
                                }
                            }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            for (event in events) {
                SystemClock.sleep(180)
                val before = ownRows(vibratorDump())
                click("触感合成 ${event.name}")
                SystemClock.sleep(350)
                val newRows = ownRows(vibratorDump()) - before
                evidence[event] = newRows
                saveEvidence(evidence)
                if (event == AppHapticEvent.Collapse) {
                    assertTrue("Closing must suppress the outer button's generic click: $newRows", newRows.isEmpty())
                } else {
                    assertTrue("$event must reach the vibrator service", newRows.isNotEmpty())
                    assertEquals("$event must emit one request despite the nested AppButton", 1, newRows.size)
                }
            }
        }
        val output = formatEvidence(evidence)
        val playedEffects = evidence.filterKeys { it != AppHapticEvent.Collapse }.mapValues { (_, rows) ->
            rows.single().substringAfter("| played: ", "").substringBefore(" | original:")
        }
        assertTrue("Service history must expose each played effect: $output", playedEffects.values.all { it.isNotBlank() })
        assertTrue("Ordinary clicks and popup expansion must feel different: $output",
            playedEffects[AppHapticEvent.Click] != playedEffects[AppHapticEvent.Expand])
        assertTrue("Switch on/off must dispatch different effects: $output",
            playedEffects[AppHapticEvent.ToggleOn] != playedEffects[AppHapticEvent.ToggleOff])
    }

    private fun formatEvidence(evidence: Map<AppHapticEvent, Set<String>>): String =
        evidence.entries.joinToString("\n") { (event, rows) ->
            "${event.name}:\n" + if (rows.isEmpty()) "  quiet" else rows.joinToString("\n") { "  $it" }
        }

    private fun saveEvidence(evidence: Map<AppHapticEvent, Set<String>>) {
        File(instrumentation.targetContext.getExternalFilesDir(null), "haptics-semantic-dispatch.txt")
            .writeText(formatEvidence(evidence))
    }

    private fun ownRows(dump: String): Set<String> = dump.lineSequence().filter {
        it.contains("| ${instrumentation.targetContext.packageName} (uid=") && it.contains("| played:")
    }.map(String::trim).toSet()

    private fun click(label: String) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val node = try { findLabel(root, label) } finally { root.recycle() }
                var candidate = node
                while (candidate != null && !candidate.isClickable) {
                    val parent = candidate.parent
                    candidate.recycle()
                    candidate = parent
                }
                val clicked = try { candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
                    finally { candidate?.recycle() }
                if (clicked) {
                    instrumentation.waitForIdleSync()
                    return
                }
            }
            SystemClock.sleep(50)
        }
        error("Synthetic haptic control was not clickable: $label")
    }

    @Suppress("DEPRECATION")
    private fun findLabel(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == label) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try { findLabel(child, label)?.let { return it } } finally { child.recycle() }
        }
        return null
    }

    private fun vibratorDump(): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("dumpsys vibrator_manager")
    ).bufferedReader().use { it.readText() }
}
