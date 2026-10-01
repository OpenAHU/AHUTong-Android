package com.ahu.ahutong.mail

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.data.mail.MailDraft
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.components.LiquidGlassAppHost
import com.ahu.ahutong.ui.screen.main.composeMailContent
import com.ahu.ahutong.ui.state.MailScreen
import com.ahu.ahutong.ui.state.StudentMailUiState
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic reply fixture: never fetches mail, saves drafts, uploads files, or sends messages. */
@RunWith(AndroidJUnit4::class)
class StudentMailComposeLongReplyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 90_000L)
    fun radiantLongReplyKeepsBoundedEditorAndReachableAttachmentAndSaveActions() {
        val body = buildString {
            append("Synthetic reply begins\n\n")
            repeat(640) { line ->
                append("> Quoted line ${line.toString().padStart(4, '0')}: synthetic mail body for scrolling and glass rendering.\n")
            }
            append("SYNTHETIC_REPLY_END")
        }
        val state = StudentMailUiState(connected = true, screen = MailScreen.COMPOSE, draftDirty = true,
            draft = MailDraft(to = "example@example.test", subject = "Re: Synthetic long reply", body = body))
        val host = AtomicReference<MainActivity?>()
        val list = AtomicReference<LazyListState?>()
        val startupFailure = AtomicReference<Throwable?>()
        val fixtureReady = CountDownLatch(1)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.RESUMED && host.compareAndSet(null, activity)) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        activity.setContent {
                            AHUTheme(AhuThemeConfig(AppUiTheme.RADIANT, "default", AppThemeMode.LIGHT)) {
                                LiquidGlassAppHost(Modifier.fillMaxSize()) {
                                    val scroll = rememberLazyListState()
                                    SideEffect { list.set(scroll) }
                                    LazyColumn(Modifier.fillMaxSize().systemBarsPadding().imePadding(), state = scroll,
                                        contentPadding = PaddingValues(vertical = 32.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        composeMailContent(state, update = {}, showContacts = {}, addAttachments = {},
                                            saveDraft = {}, removeAttachment = {}, previewSaved = {})
                                    }
                                }
                            }
                        }
                    } catch (failure: Throwable) { startupFailure.set(failure) }
                    finally { fixtureReady.countDown() }
                }
            }
        }
        monitor.addLifecycleCallback(callback)
        try {
            val component = "${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}"
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -n $component"))
                .bufferedReader().use { it.readText() }
            assertTrue("Long reply fixture must be installed", fixtureReady.await(15, TimeUnit.SECONDS))
            startupFailure.get()?.let { throw AssertionError("Cannot install long reply fixture", it) }
            val activity = requireNotNull(host.get())
            await { list.get() != null }
            val scroll = requireNotNull(list.get())
            // With no CC or attachments, body is the third independent lazy item.
            runBlocking { withContext(Dispatchers.Main) { scroll.scrollToItem(2) } }
            await { findNode { it.isEditable && it.text?.toString() == body }?.let { node -> recycle(node); true } == true }

            var bodyItemHeight = 0
            instrumentation.runOnMainSync {
                bodyItemHeight = scroll.layoutInfo.visibleItemsInfo.first { it.key == "editor-body" }.size
            }
            val density = activity.resources.displayMetrics.density
            assertTrue("Editor card must fit 320dp editor plus 36dp card padding, got $bodyItemHeight pixels",
                bodyItemHeight <= ((320 + 36) * density).toInt() + 4)
            val editor = requireNotNull(findNode { it.isEditable && it.text?.toString() == body })
            val editorBounds = bounds(editor)
            assertTrue("Editor must have useful visible height", editorBounds.height() > (160 * density).toInt())
            assertTrue("Native editor height must remain bounded", editorBounds.height() <= (320 * density).toInt() + 4)
            screenshot(activity, "long-reply-top")
            val topViewport = editorViewport(activity, "long-reply-top-viewport", editorBounds)
            assertTrue("Editor must accept input focus before moving its caret",
                editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
            recycle(editor)
            await {
                findNode { it.isEditable && it.text?.toString() == body }?.let { node ->
                    val focused = node.isFocused
                    recycle(node)
                    focused
                } == true
            }
            // Let the new input connection finish initialization before changing selection.
            // IME startup can publish its original 0..0 selection after an immediate accessibility action.
            SystemClock.sleep(1000)
            instrumentation.waitForIdleSync()
            val focusedEditor = requireNotNull(findNode { it.isEditable && it.text?.toString() == body })
            val selection = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, body.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, body.length)
            }
            assertTrue("Editor must support moving the caret to the final quoted line",
                focusedEditor.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection))
            recycle(focusedEditor)
            SystemClock.sleep(400)
            val caretReachedEnd = findNode { it.isEditable && it.text?.toString() == body }?.let { node ->
                val selected = node.textSelectionEnd == body.length
                recycle(node)
                selected
            } == true
            if (!caretReachedEnd) {
                // Exercise the real editor keyboard handler when the accessibility action reports
                // success without changing selection. Ctrl+End moves to the end of the document.
                moveCaretToDocumentEnd()
            }
            var selectionDiagnostic = "No refreshed editor node"
            try {
                await(diagnostic = { selectionDiagnostic }) {
                    findNode { it.isEditable && it.text?.toString() == body }?.let { node ->
                        selectionDiagnostic = "focused=${node.isFocused}, selection=${node.textSelectionStart}..${node.textSelectionEnd}, " +
                            "characterLocationSupported=${node.availableExtraData.contains(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY)}"
                        val selected = node.textSelectionEnd == body.length
                        recycle(node)
                        selected
                    } == true
                }
            } catch (failure: AssertionError) {
                screenshot(activity, "long-reply-scroll-failure")
                topViewport.recycle()
                throw failure
            }
            screenshot(activity, "long-reply-end")
            val endEditor = requireNotNull(findNode { it.isEditable && it.text?.toString() == body })
            val endViewport = editorViewport(activity, "long-reply-end-viewport", bounds(endEditor))
            recycle(endEditor)
            try {
                // Compose's accessibility character rectangles do not account for this editor's
                // internal scroll offset. Check the actual painted viewport instead of those coordinates.
                assertViewportChanged(topViewport, endViewport)
            } finally { topViewport.recycle(); endViewport.recycle() }
            findNode { it.isEditable && it.text?.toString() == body }?.let { node ->
                node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
                recycle(node)
            }
            instrumentation.runOnMainSync {
                activity.getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            }
            // Scrolling the outer list exposes actions without traversing every quoted line.
            runBlocking { withContext(Dispatchers.Main) { scroll.scrollToItem(3) } }
            await { hasVisibleText("添加附件") && hasVisibleText("保存草稿") }
            screenshot(activity, "long-reply-actions")
        } finally {
            monitor.removeLifecycleCallback(callback)
            instrumentation.runOnMainSync { host.get()?.finish() }
        }
    }

    private fun await(diagnostic: (() -> String)? = null, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 12_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Long reply UI did not reach the expected visible state" +
            diagnostic?.let { ": ${it()}" }.orEmpty())
    }

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        return try { findNode(root, predicate) } finally { recycle(root) }
    }

    private fun findNode(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        @Suppress("DEPRECATION")
        if (node.isVisibleToUser && predicate(node)) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val result = try { findNode(child, predicate) } finally { recycle(child) }
            if (result != null) return result
        }
        return null
    }

    private fun hasVisibleText(text: String): Boolean = findNode { it.text?.toString() == text }?.let {
        val visible = !bounds(it).isEmpty
        recycle(it)
        visible
    } == true

    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)

    private fun moveCaretToDocumentEnd() {
        val downTime = SystemClock.uptimeMillis()
        val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        val events = listOf(
            Triple(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, ctrl),
            Triple(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END, ctrl),
            Triple(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MOVE_END, ctrl),
            Triple(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0)
        )
        events.forEachIndexed { index, (action, keyCode, meta) ->
            val event = KeyEvent(downTime, downTime + index * 50L, action, keyCode, 0, meta).apply {
                source = InputDevice.SOURCE_KEYBOARD
            }
            assertTrue("Could not inject the editor Ctrl+End key sequence",
                instrumentation.uiAutomation.injectInputEvent(event, true))
            SystemClock.sleep(50)
        }
    }

    private fun editorViewport(activity: MainActivity, name: String, bounds: Rect): Bitmap {
        val screen = instrumentation.uiAutomation.takeScreenshot()
        val clipped = Rect(bounds)
        assertTrue("Editor viewport must intersect the screenshot", clipped.intersect(0, 0, screen.width, screen.height))
        val viewport = Bitmap.createBitmap(screen, clipped.left, clipped.top, clipped.width(), clipped.height())
        if (viewport !== screen) screen.recycle()
        File(activity.getExternalFilesDir(null), "student-mail-$name.png").outputStream().use {
            viewport.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return viewport
    }

    private fun assertViewportChanged(before: Bitmap, after: Bitmap) {
        val width = minOf(before.width, after.width)
        val height = minOf(before.height, after.height)
        assertTrue("Both editor viewports must have useful dimensions", width > 100 && height > 100)
        var samples = 0
        var changed = 0
        var beforeInk = 0
        var afterInk = 0
        for (y in 0 until height step 2) for (x in 0 until width step 2) {
            val old = before.getPixel(x, y)
            val new = after.getPixel(x, y)
            if (Color.red(old) + Color.green(old) + Color.blue(old) < 450) beforeInk++
            if (Color.red(new) + Color.green(new) + Color.blue(new) < 450) afterInk++
            val difference = kotlin.math.abs(Color.red(old) - Color.red(new)) +
                kotlin.math.abs(Color.green(old) - Color.green(new)) + kotlin.math.abs(Color.blue(old) - Color.blue(new))
            if (difference > 60) changed++
            samples++
        }
        assertTrue("Initial viewport must visibly paint mail text", beforeInk > maxOf(100, samples / 600))
        assertTrue("Final viewport must visibly paint mail text", afterInk > maxOf(100, samples / 600))
        assertTrue("Moving the caret to the end must noticeably change painted content: $changed/$samples samples",
            changed > maxOf(200, samples / 500))
    }

    private fun screenshot(activity: MainActivity, name: String) {
        SystemClock.sleep(600)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(activity.getExternalFilesDir(null), "student-mail-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) = node.recycle()
}
