package com.ahu.ahutong.ui.components

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A static fixture exercises window placement without changing payment or alert data. */
@RunWith(AndroidJUnit4::class)
class DialogDropdownWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 60_000L)
    fun miuixRoomDropdownInsideDialogIsVisibleNearAnchorAndSelectsRoom() {
        val callbackSelection = AtomicReference<String?>(null)
        val activity = AtomicReference<MainActivity?>(null)
        val startupFailure = AtomicReference<Throwable?>(null)
        val fixtureReady = CountDownLatch(1)
        val mainHandler = Handler(Looper.getMainLooper())
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { candidate, stage ->
            if (candidate is MainActivity && stage == Stage.RESUMED &&
                activity.compareAndSet(null, candidate)
            ) {
                mainHandler.post {
                    try {
                        installFixture(candidate, callbackSelection)
                    } catch (failure: Throwable) {
                        startupFailure.set(failure)
                    } finally {
                        fixtureReady.countDown()
                    }
                }
            }
        }
        monitor.addLifecycleCallback(callback)
        try {
            val component = "${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}"
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("am start -n $component")
            ).bufferedReader().use { it.readText() }
            if (!fixtureReady.await(15, TimeUnit.SECONDS)) {
                val mainThread = Looper.getMainLooper().thread
                val stack = Thread.getAllStackTraces()[mainThread]
                    ?.joinToString("\n") { "    at $it" } ?: "Main thread stack unavailable"
                throw AssertionError("MainActivity fixture was not installed within 15 seconds.\n" +
                    "Main thread (${mainThread.name}, ${mainThread.state}):\n$stack")
            }
            startupFailure.get()?.let {
                throw AssertionError("Could not launch MainActivity or install fixture").apply { initCause(it) }
            }

            awaitVisibleBounds("已选择：$ROOM_A")
            val anchor = awaitVisibleBounds("房间选择器")
            tap(anchor)
            // Querying only the active window rejects a menu drawn behind the Dialog.
            val menuItem = awaitVisibleBounds(ROOM_B)
            val density = instrumentation.targetContext.resources.displayMetrics.density
            val horizontalGap = max(0, max(anchor.left - menuItem.right, menuItem.left - anchor.right))
            val verticalGap = max(0, max(anchor.top - menuItem.bottom, menuItem.top - anchor.bottom))
            assertTrue("Dropdown is horizontally detached from anchor: $anchor / $menuItem",
                horizontalGap <= (32 * density).toInt())
            assertTrue("Dropdown is vertically detached from anchor: $anchor / $menuItem",
                verticalGap <= (120 * density).toInt())
            tap(menuItem)
            awaitVisibleBounds("已选择：$ROOM_B")
            assertEquals(ROOM_B, callbackSelection.get())
        } finally {
            monitor.removeLifecycleCallback(callback)
            mainHandler.post { activity.get()?.finish() }
        }
    }

    private fun installFixture(activity: MainActivity, callbackSelection: AtomicReference<String?>) {
        activity.setContent {
            AHUTheme(
                AhuThemeConfig(
                    appUiTheme = AppUiTheme.MIUIX,
                    themeColorHex = null,
                    themeMode = AppThemeMode.FOLLOW_SYSTEM
                )
            ) {
                Box(Modifier.fillMaxSize()) {
                    var selected by remember { mutableStateOf(ROOM_A) }
                    AppDialog(
                        title = "下拉窗口回归",
                        onDismiss = {},
                        content = {
                            AppSelectField(
                                label = "需要充值的房间",
                                modifier = Modifier.semantics {
                                    contentDescription = "房间选择器"
                                },
                                selected = selected,
                                options = listOf(
                                    AppSelectOption(ROOM_A, ROOM_A),
                                    AppSelectOption(ROOM_B, ROOM_B)
                                ),
                                onSelected = {
                                    selected = it
                                    callbackSelection.set(it)
                                }
                            )
                            Text("已选择：$selected")
                        }
                    )
                }
            }
        }
    }

    private fun awaitVisibleBounds(text: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val found = visibleBounds(root, text)
                @Suppress("DEPRECATION")
                root.recycle()
                if (found != null) return found
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("No visible '$text' in the active window within 10 seconds")
    }

    private fun visibleBounds(node: AccessibilityNodeInfo, text: String): Rect? {
        if (node.isVisibleToUser &&
            (node.text?.toString() == text || node.contentDescription?.toString() == text)
        ) {
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
            val event = MotionEvent.obtain(
                downTime, downTime + index * 50L, action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                assertTrue("Could not inject tap at $bounds",
                    instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }

    private companion object {
        const val ROOM_A = "房间A"
        const val ROOM_B = "房间B"
    }
}
