package com.ahu.ahutong.ui

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.feedback.FEEDBACK_EXPORT_ROUTE
import com.ahu.ahutong.feedback.FEEDBACK_FORM_ROUTE
import com.ahu.ahutong.feedback.FEEDBACK_HUB_ROUTE
import com.ahu.ahutong.feedback.FEEDBACK_ROUTE
import com.ahu.ahutong.feedback.feedbackDestinations
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import com.ahu.ahutong.utils.animatedComposable
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the production navigation graph without startup/account flows or feedback/log actions. */
@RunWith(AndroidJUnit4::class)
class FeedbackNavigationWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 60_000)
    fun feedbackChildrenReturnThroughTheHostBackStack() {
        val controller = AtomicReference<NavHostController>()
        launchIsolatedTestActivity().use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    AHUTheme(AhuThemeConfig(AppUiTheme.MATERIAL, null, AppThemeMode.FOLLOW_SYSTEM)) {
                        val navController = rememberNavController()
                        SideEffect { controller.set(navController) }
                        NavHost(navController, startDestination = "test_settings") {
                            animatedComposable("test_settings") {
                                Column {
                                    Text("测试设置")
                                    AppButton(onClick = { navController.navigate(FEEDBACK_ROUTE) }) {
                                        Text("进入意见反馈")
                                    }
                                }
                            }
                            feedbackDestinations(navController, onExit = { navController.popBackStack() })
                        }
                    }
                }
            }
            awaitRoute(controller, "test_settings")
            click("进入意见反馈")
            awaitRoute(controller, FEEDBACK_HUB_ROUTE)
            click("问题反馈")
            awaitRoute(controller, FEEDBACK_FORM_ROUTE)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitRoute(controller, FEEDBACK_HUB_ROUTE)
            click("日志导出")
            awaitRoute(controller, FEEDBACK_EXPORT_ROUTE)
            click("返回")
            awaitRoute(controller, FEEDBACK_HUB_ROUTE)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitRoute(controller, "test_settings")
        }
    }

    @Test(timeout = 60_000)
    fun directErrorEntryCreatesAHubWhenReturningFromTheForm() {
        val controller = AtomicReference<NavHostController>()
        val exits = AtomicInteger()
        launchIsolatedTestActivity().use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    AHUTheme(AhuThemeConfig(AppUiTheme.MATERIAL, null, AppThemeMode.FOLLOW_SYSTEM)) {
                        val navController = rememberNavController()
                        SideEffect { controller.set(navController) }
                        NavHost(navController, startDestination = FEEDBACK_ROUTE) {
                            feedbackDestinations(navController,
                                onExit = { exits.incrementAndGet() },
                                errorDetails = "Synthetic navigation error; no log collection")
                        }
                    }
                }
            }
            awaitRoute(controller, FEEDBACK_FORM_ROUTE)
            click("返回")
            awaitRoute(controller, FEEDBACK_HUB_ROUTE)
            assertEquals(0, exits.get())
            click("返回")
            assertEquals(1, exits.get())
        }
    }

    private fun awaitRoute(controller: AtomicReference<NavHostController>, expected: String) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var actual: String? = null
        while (SystemClock.uptimeMillis() < deadline) {
            var settled = false
            instrumentation.runOnMainSync {
                val entry = controller.get()?.currentBackStackEntry
                actual = entry?.destination?.route
                settled = entry?.lifecycle?.currentState == Lifecycle.State.RESUMED
            }
            if (actual == expected && settled) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Navigation destination did not settle: expected=$expected actual=$actual")
    }

    private fun click(label: String) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var foundTarget = false
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                try {
                    val node = find(root, label)
                    if (node != null) {
                        var handled = false
                        try {
                            var target = AccessibilityNodeInfo.obtain(node)
                            while (!target.isClickable) {
                                val parent = target.parent ?: break
                                target.recycle()
                                target = parent
                            }
                            try {
                                if (target.isClickable && target.isEnabled && target.isVisibleToUser) {
                                    foundTarget = true
                                    handled = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                }
                            } finally { target.recycle() }
                        } finally { node.recycle() }
                        if (handled) {
                            instrumentation.waitForIdleSync()
                            return
                        }
                    }
                } finally { root.recycle() }
            }
            SystemClock.sleep(50)
        }
        throw AssertionError(if (foundTarget) "'$label' did not handle a click before the deadline"
            else "No enabled visible '$label' in feedback navigation")
    }

    private fun find(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try { find(child, label)?.let { return it } } finally { child.recycle() }
        }
        return null
    }
}
