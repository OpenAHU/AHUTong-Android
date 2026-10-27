package com.ahu.ahutong.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.FeedbackActivity
import com.ahu.ahutong.core.common.diagnostics.FeedbackDiagnostics
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Activity + SAF result + FileProvider, with external picker and chooser intercepted. */
@RunWith(AndroidJUnit4::class)
class FeedbackExportActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test(timeout = 90_000)
    fun exportsAndSharesOnlyEncryptedLogsWithoutRequiringADescription() {
        val directory = File(context.cacheDir, "feedback-exports").apply { mkdirs() }
        val destination = File(directory, "instrumentation-save.ahulog").apply { delete(); createNewFile() }
        val documentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination)
        val shared = AtomicReference<Intent>()
        val sharedReady = CountDownLatch(1)
        val pickerReady = CountDownLatch(1)
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? = when (intent.action) {
                Intent.ACTION_CREATE_DOCUMENT -> {
                    pickerReady.countDown()
                    Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(documentUri))
                }
                Intent.ACTION_CHOOSER -> {
                    @Suppress("DEPRECATION")
                    val inner = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    shared.set(inner)
                    sharedReady.countDown()
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
                Intent.ACTION_SEND -> {
                    shared.set(intent)
                    sharedReady.countDown()
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
                else -> null
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch<FeedbackActivity>(FeedbackActivity.intent(context)).use {
                click("日志导出")
                FeedbackDiagnostics.clear()
                FeedbackDiagnostics.recordRuntime("synthetic-feedback-export-runtime")
                FeedbackDiagnostics.recordError("synthetic-feedback-export-error password=synthetic-password")
                click("导出日志")
                assertTrue("CreateDocument was not launched", pickerReady.await(15, TimeUnit.SECONDS))
                assertEncrypted(awaitExportBytes(destination))

                click("分享日志")
                assertTrue("System share chooser was not launched", sharedReady.await(15, TimeUnit.SECONDS))
                val share = requireNotNull(shared.get())
                assertEquals(Intent.ACTION_SEND, share.action)
                assertEquals("application/octet-stream", share.type)
                assertTrue("Recipient must get temporary read permission",
                    share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                @Suppress("DEPRECATION")
                val uri = share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                assertNotNull(uri)
                assertEquals("content", uri!!.scheme)
                assertEquals("${context.packageName}.fileprovider", uri.authority)
                assertEquals("ClipData must carry the same granted content URI", uri,
                    share.clipData?.getItemAt(0)?.uri)
                val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
                assertEncrypted(bytes)
                context.contentResolver.delete(uri, null, null)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            destination.delete()
            FeedbackDiagnostics.clear()
        }
    }

    private fun awaitExportBytes(file: File): ByteArray {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val bytes = runCatching { file.readBytes() }.getOrNull()
            if (bytes != null && runCatching {
                    JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject["ciphertext"] != null
                }.getOrDefault(false)) return bytes
            SystemClock.sleep(80)
        }
        throw AssertionError("Activity did not write the encrypted export to the selected document")
    }

    private fun assertEncrypted(bytes: ByteArray) {
        val text = bytes.toString(Charsets.UTF_8)
        val envelope = JsonParser.parseString(text).asJsonObject
        assertEquals("AHUTONG-LOG-1", envelope["format"].asString)
        assertEquals("RSA-OAEP-SHA256+A256GCM", envelope["algorithm"].asString)
        assertTrue(envelope["wrappedKey"].asString.isNotBlank())
        assertTrue(envelope["nonce"].asString.isNotBlank())
        assertTrue(envelope["ciphertext"].asString.isNotBlank())
        assertFalse(text.contains("synthetic-feedback-export-runtime"))
        assertFalse(text.contains("synthetic-feedback-export-error"))
        assertFalse(text.contains("synthetic-password"))
    }

    private fun click(label: String) {
        val deadline = SystemClock.uptimeMillis() + 12_000
        var direction = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                try {
                    val node = find(root) {
                        it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label)
                    }
                    if (node != null) {
                        try {
                            var target: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
                            try {
                                while (target != null && !target.isClickable) {
                                    val parent = target.parent
                                    target.recycleNode()
                                    target = parent
                                }
                                if (target?.isEnabled == true && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                    return
                                }
                            } finally { target?.recycleNode() }
                        } finally { node.recycleNode() }
                    }
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
            SystemClock.sleep(100)
        }
        throw AssertionError("No enabled '$label' control in FeedbackActivity")
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
