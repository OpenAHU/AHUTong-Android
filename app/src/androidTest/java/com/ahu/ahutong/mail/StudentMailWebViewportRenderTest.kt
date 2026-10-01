package com.ahu.ahutong.mail

import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ahu.ahutong.MainActivity
import com.ahu.ahutong.ui.screen.main.StudentMailWebViewport
import com.ahu.ahutong.ui.screen.main.StudentMailWebPage
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Reproduces the official fixed-width CSS and zoom-blocking meta without any mailbox requests. */
@RunWith(AndroidJUnit4::class)
class StudentMailWebViewportRenderTest {
    @Test fun desktopPageRightEdgeFitsPhoneAndRemainsZoomable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val host = AtomicReference<MainActivity?>()
        val page = AtomicReference<WebView?>()
        val failure = AtomicReference<Throwable?>()
        val loaded = CountDownLatch(1)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.RESUMED && host.compareAndSet(null, activity)) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        val web = WebView(activity)
                        page.set(web)
                        web.settings.javaScriptEnabled = true
                        web.settings.blockNetworkLoads = true
                        web.settings.allowFileAccess = false
                        web.settings.allowContentAccess = false
                        StudentMailWebViewport.configure(web)
                        activity.setContent {
                            AHUTheme(AhuThemeConfig(AppUiTheme.RADIANT, "default", AppThemeMode.LIGHT)) {
                                StudentMailWebPage(web, onBack = {}, onFit = { StudentMailWebViewport.fit(web) })
                            }
                        }
                        web.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                view.post { StudentMailWebViewport.fit(view); loaded.countDown() }
                            }
                        }
                        web.loadDataWithBaseURL("https://mail.stu.ahu.edu.cn/viewport-fixture", """
                            <!doctype html><html><head>
                            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
                            <style>html,body{margin:0;height:100%;overflow:hidden}
                            #desktop{width:100%;min-width:1164px;height:100vh;position:relative;background:#edf2ff}
                            #right{position:absolute;right:0;top:0;width:64px;height:100%;background:#008000}</style>
                            </head><body><div id="desktop"><span>Desktop mailbox fixture</span><div id="right">Right</div></div></body></html>
                        """.trimIndent(), "text/html", "UTF-8", null)
                    } catch (error: Throwable) { failure.set(error); loaded.countDown() }
                }
            }
        }
        monitor.addLifecycleCallback(callback)
        try {
            val component = "${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}"
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am start -n $component"))
                .bufferedReader().use { it.readText() }
            assertTrue("Desktop fixture must load", loaded.await(15, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Cannot install viewport fixture", it) }
            val web = requireNotNull(page.get())
            var fitted = false
            var metrics: JSONObject? = null
            repeat(40) {
                if (!fitted) {
                    val result = AtomicReference<String?>()
                    val evaluated = CountDownLatch(1)
                    instrumentation.runOnMainSync {
                        web.evaluateJavascript("""
                            (function(){return {width:window.innerWidth,visible:window.visualViewport.width,
                            height:window.visualViewport.height,desktopHeight:document.getElementById('desktop').clientHeight,
                            right:document.getElementById('right').getBoundingClientRect().right,
                            viewport:document.querySelector('meta[name="viewport"]').content};})()
                        """.trimIndent()) { value -> result.set(value); evaluated.countDown() }
                    }
                    assertTrue(evaluated.await(2, TimeUnit.SECONDS))
                    metrics = JSONObject(requireNotNull(result.get()))
                    fitted = metrics!!.getDouble("width") >= 1279 && metrics!!.getDouble("visible") >= 1275
                    if (!fitted) Thread.sleep(100)
                }
            }
            assertTrue("Desktop page must be scaled to fit the full viewport: $metrics", fitted)
            val measured = requireNotNull(metrics)
            assertTrue("The rightmost mailbox control must be inside the visible viewport",
                measured.getDouble("right") <= measured.getDouble("visible") + 2)
            assertTrue("The official viewport-height layout must fill the browser area",
                measured.getDouble("desktopHeight") >= measured.getDouble("height") - 2)
            assertTrue(measured.getString("viewport").contains("user-scalable=yes"))
            instrumentation.runOnMainSync {
                assertTrue("The page toolbar must leave a visible browser area", web.height > 200 * web.resources.displayMetrics.density)
                assertTrue(web.settings.supportZoom())
                assertTrue(web.settings.builtInZoomControls)
                assertFalse(web.settings.displayZoomControls)
                assertTrue(web.isHorizontalScrollBarEnabled)
                assertFalse(web.settings.userAgentString.contains("Android"))
                assertFalse(web.settings.userAgentString.contains("Mobile"))
            }
        } finally {
            monitor.removeLifecycleCallback(callback)
            instrumentation.runOnMainSync { page.get()?.destroy(); host.get()?.finish() }
        }
    }
}
