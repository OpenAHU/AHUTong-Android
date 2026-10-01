package com.ahu.ahutong.mail

import android.webkit.WebView
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.data.mail.*
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.screen.main.StudentMailBodyContent
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Compose/WebView integration with synthetic documents, never server mail. */
@RunWith(AndroidJUnit4::class)
class StudentMailBodyRenderTest {
    @Test fun htmlLayoutLoadsCidWithoutScriptsOrWebViewNetworkAccess() {
        val loaded = AtomicBoolean()
        val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url
            if (url.queryParameter("func") == "mbox:getMessageData" && url.queryParameter("part") == "2") loaded.set(true)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "image/png").body(png.toResponseBody("image/png".toMediaType())).build()
        }.build()
        val session = MailSession("synthetic-session", "mail.stu.ahu.edu.cn", client, "example", { true }, MailMemoryCookieJar())
        val detail = MailDetail("synthetic", "HTML example", emptyList(), emptyList(), emptyList(), "",
            """<style>.banner{background:#e8f0fe;color:#172554;padding:14px}</style>
                <h1 class="banner">HTML layout example</h1><p>Text with <b>bold</b>, <i>italics</i> and <a href="https://example.test">a link</a>.</p>
                <table border="1"><tr><th>Item</th><th>Status</th></tr><tr><td>Native HTML</td><td>Ready</td></tr></table>
                <blockquote>Quoted message</blockquote><pre><code>val ready = true</code></pre>
                <img src="cid:sample-image" width="64" height="64" alt="Inline image">
                <script>document.body.textContent='must not run'</script>""",
            listOf(MailAttachment("2", "sample.png", png.size.toLong(), messageId = "synthetic", partId = "2",
                contentId = "sample-image", contentType = "image/png", inlined = true)))
        try {
            render(detail, "html", { mail, external -> StudentMailBodyImages(session, mail.id, mail.attachments, external) }) { web ->
                assertFalse(web.settings.javaScriptEnabled)
                assertFalse(web.settings.allowFileAccess)
                assertFalse(web.settings.allowContentAccess)
                assertTrue(web.settings.blockNetworkLoads)
            }
            assertTrue("CID image must reach the intercepted native loader", loaded.get())
        } finally { client.dispatcher.cancelAll(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun markdownTablesCodeAndQuotesRenderInNativeBody() {
        val detail = MailDetail("markdown-example", "Markdown example", emptyList(), emptyList(), emptyList(),
            """
                # Markdown example

                **Bold** and *italic* with [a link](https://example.test).

                | Item | Status |
                | --- | --- |
                | Markdown | Ready |

                > Quoted message

                ```kotlin
                val ready = true
                ```

                - First item
                - Second item
            """.trimIndent(), "", emptyList())
        render(detail, "markdown", { _, _ -> null }) { web -> assertTrue(web.contentHeight > 200) }
    }

    private fun render(detail: MailDetail, name: String, images: (MailDetail, Boolean) -> StudentMailBodyImages?,
        assertion: (WebView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val host = AtomicReference<MainActivity?>()
        val startupFailure = AtomicReference<Throwable?>()
        val fixtureReady = CountDownLatch(1)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.RESUMED && host.compareAndSet(null, activity)) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        activity.setContent {
                            AHUTheme(AhuThemeConfig(AppUiTheme.MIUIX, "default", AppThemeMode.LIGHT)) {
                                Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState())) {
                                    StudentMailBodyContent(detail, images)
                                }
                            }
                        }
                    } catch (error: Throwable) { startupFailure.set(error) }
                    finally { fixtureReady.countDown() }
                }
            }
        }
        monitor.addLifecycleCallback(callback)
        try {
            val component = "${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}"
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -n $component"))
                .bufferedReader().use { it.readText() }
            assertTrue("Mail fixture must be installed", fixtureReady.await(15, TimeUnit.SECONDS))
            startupFailure.get()?.let { throw AssertionError("Cannot install mail fixture", it) }
            val activity = requireNotNull(host.get())
            var ready = false
            repeat(40) {
                if (!ready) {
                    instrumentation.runOnMainSync {
                        val web = webView(activity.window.decorView)
                        ready = web != null && web.contentHeight > 0 && web.height > 0
                    }
                    if (!ready) Thread.sleep(100)
                }
            }
            assertTrue("Mail body must finish layout", ready)
            Thread.sleep(2500)
            instrumentation.runOnMainSync { assertion(requireNotNull(webView(activity.window.decorView))) }
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            File(activity.getExternalFilesDir(null), "student-mail-body-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        } finally {
            monitor.removeLifecycleCallback(callback)
            instrumentation.runOnMainSync { host.get()?.finish() }
        }
    }

    private fun webView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) webView(view.getChildAt(index))?.let { return it }
        return null
    }
}
