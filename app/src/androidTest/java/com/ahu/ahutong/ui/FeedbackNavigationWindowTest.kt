package com.ahu.ahutong.ui

import android.animation.ValueAnimator
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.FeedbackActivity
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Observes the real Activity's Compose layout on draw frames, independently of accessibility IPC. */
@RunWith(AndroidJUnit4::class)
class FeedbackNavigationWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test(timeout = 60_000)
    fun feedbackChildrenAndBothBackActionsHaveIntermediateHorizontalMotion() {
        assertTrue("Navigation animation regression requires enabled system animators", ValueAnimator.areAnimatorsEnabled())
        val trace = StringBuilder("case,elapsedMs,left,width\n")
        try {
            ActivityScenario.launch<FeedbackActivity>(FeedbackActivity.intent(context)).use { scenario ->
                awaitText("QQ反馈群").recycleNode()
                instrumentation.uiAutomation.waitForIdle(300, 3_000)
                assertMotion(scenario, "hub_feedback", "预览反馈", entering = true, trace = trace) { click("问题反馈") }
                assertMotion(scenario, "toolbar_pop", "QQ反馈群", entering = false, trace = trace) { click("返回") }
                assertMotion(scenario, "hub_export", "分享日志", entering = true, trace = trace) { click("日志导出") }
                assertMotion(scenario, "system_pop", "QQ反馈群", entering = false, trace = trace) { back() }
            }
        } finally {
            File(context.getExternalFilesDir(null), "feedback-navigation-motion.csv").writeText(trace.toString())
        }
    }

    @Test(timeout = 60_000)
    fun previewConsumesBackAndChildDraftAndDestinationSurviveRecreation() {
        val description = "合成导航回归：保留反馈草稿"
        ActivityScenario.launch<FeedbackActivity>(FeedbackActivity.intent(context)).use { scenario ->
            click("问题反馈")
            val editor = awaitNode { it.isEditable }
            try {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, description)
                }
                assertTrue(editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
            } finally { editor.recycleNode() }
            awaitNode { it.isEditable && it.text?.toString() == description }.recycleNode()
            click("预览反馈")
            awaitText("反馈预览").recycleNode()
            back()
            awaitAbsent("反馈预览")
            awaitText("预览反馈").recycleNode()
            awaitNode { it.isEditable && it.text?.toString() == description }.recycleNode()
            click("返回")
            click("日志导出")
            awaitText("分享日志").recycleNode()
            back()
            click("问题反馈")
            awaitNode { it.isEditable && it.text?.toString() == description }.recycleNode()
            scenario.recreate()
            awaitText("预览反馈").recycleNode()
            awaitNode { it.isEditable && it.text?.toString() == description }.recycleNode()
        }
    }

    @Test(timeout = 30_000)
    fun directErrorEntryBackReturnsToTheFeedbackHub() {
        ActivityScenario.launch<FeedbackActivity>(FeedbackActivity.intent(context, "合成错误上下文")).use {
            awaitText("预览反馈").recycleNode()
            back()
            awaitText("QQ反馈群").recycleNode()
        }
    }

    private fun assertMotion(
        scenario: ActivityScenario<FeedbackActivity>,
        name: String,
        target: String,
        entering: Boolean,
        trace: StringBuilder,
        trigger: () -> Unit
    ) {
        val samples = mutableListOf<Triple<Long, Float, Int>>()
        var stop: (() -> Unit)? = null
        var settledX = Float.NaN
        scenario.onActivity { activity ->
            val root = requireNotNull(findComposeRoot(activity.window.decorView)) { "Activity has no Compose root" }
            val clock = Choreographer.getInstance()
            var active = true
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!active) return
                    val node = findText(root.semanticsOwner.unmergedRootSemanticsNode, target)
                    if (node != null && node.layoutInfo.isAttached && node.layoutInfo.isPlaced &&
                        node.size.width > 0 && node.size.height > 0) {
                        val x = node.positionInRoot.x
                        if (x.isFinite()) samples += Triple(SystemClock.uptimeMillis(), x, node.size.width)
                    }
                    clock.postFrameCallback(this)
                }
            }
            stop = { active = false; clock.removeFrameCallback(callback) }
            clock.postFrameCallback(callback)
        }
        val start = SystemClock.uptimeMillis()
        try {
            trigger()
            SystemClock.sleep(650)
        } finally {
            // This main-thread barrier also makes every captured frame visible to this thread.
            scenario.onActivity { activity ->
                stop?.invoke()
                val root = requireNotNull(findComposeRoot(activity.window.decorView))
                settledX = findText(root.semanticsOwner.unmergedRootSemanticsNode, target)?.positionInRoot?.x ?: Float.NaN
            }
        }
        val emitted = samples.filter { it.first >= start }
        emitted.forEach { trace.appendLine("$name,${it.first - start},${it.second},${it.third}") }
        val positions = emitted.map { it.second }
        awaitText(target).recycleNode()
        val requiredShift = 4f * context.resources.displayMetrics.density
        assertTrue("No in-flight horizontal transition for $name: $positions",
            positions.size >= 2 && positions.max() - positions.min() >= requiredShift)
        assertTrue("Destination did not settle for $name", settledX.isFinite())
        val directedShift = if (entering) positions.max() - settledX else settledX - positions.min()
        assertTrue("Incorrect forward/pop motion for $name: $positions", directedShift >= requiredShift)
        instrumentation.uiAutomation.waitForIdle(300, 3_000)
    }

    private fun findComposeRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findComposeRoot(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun findText(node: SemanticsNode, text: String): SemanticsNode? {
        if (!node.layoutInfo.isAttached || !node.layoutInfo.isPlaced) return null
        if (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true) return node
        node.children.forEach { findText(it, text)?.let { match -> return match } }
        return null
    }

    private fun click(label: String) {
        val node = awaitText(label)
        try {
            if (label == "返回") {
                tap(Rect().also(node::getBoundsInScreen))
                return
            }
            var target: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
            try {
                while (target != null && !target.isClickable) {
                    val parent = target.parent
                    target.recycleNode()
                    target = parent
                }
                assertTrue("'$label' must handle navigation", target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            } finally { target?.recycleNode() }
        } finally { node.recycleNode() }
    }

    private fun tap(bounds: Rect) {
        assertTrue("Toolbar back icon must have visible bounds", !bounds.isEmpty)
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try {
                assertTrue("Toolbar back did not receive touch", instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(40)
        }
    }

    private fun back() = instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    private fun awaitText(label: String): AccessibilityNodeInfo = awaitNode {
        it.text?.toString() == label || it.contentDescription?.toString() == label
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8_000
        var direction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        while (SystemClock.uptimeMillis() < deadline) {
            val root = freshRoot()
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
            SystemClock.sleep(40)
        }
        throw AssertionError("No visible feedback navigation destination")
    }

    private fun awaitAbsent(label: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = freshRoot()
            if (root != null) {
                try {
                    val node = find(root) { it.isVisibleToUser && it.text?.toString() == label }
                    try { if (node == null) return } finally { node?.recycleNode() }
                } finally { root.recycleNode() }
            }
            SystemClock.sleep(40)
        }
        throw AssertionError("Preview did not consume Back")
    }

    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try { find(child, predicate)?.let { return it } } finally { child.recycleNode() }
        }
        return null
    }

    private fun freshRoot(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        if (Build.VERSION.SDK_INT < 34 && !root.refresh()) {
            root.recycleNode()
            return null
        }
        return root
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.recycleNode() = recycle()
}
