package com.ahu.ahutong.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ahu.ahutong.R
import com.ahu.ahutong.appwidget.ScheduleWidgetPreviewContent
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.screen.main.DesktopScheduleWidgetPreview
import com.ahu.ahutong.ui.theme.AHUTheme
import com.ahu.ahutong.ui.theme.AhuThemeConfig
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only fixed example courses and an isolated UI host; never reads an academic account. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class DesktopWidgetPreviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 60_000)
    fun productionPreviewLoadsInsideItsActualComposeAndAndroidViewWindow() {
        val context = instrumentation.targetContext
        assertEquals("This compact-window test expects the disposable emulator's normal font scale",
            1f, context.resources.configuration.fontScale, 0.001f)
        val measuredHeight = AtomicInteger()
        val measuredBounds = AtomicReference<Rect?>()
        var dayBackgroundColor: Int? = null
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            for (night in listOf(false, true)) {
                measuredHeight.set(0)
                measuredBounds.set(null)
                scenario.onActivity { activity ->
                    val widgetContext = activity.createConfigurationContext(Configuration(activity.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    })
                    activity.setContent {
                        key(night) {
                            AHUTheme(AhuThemeConfig(AppUiTheme.MATERIAL, null,
                                if (night) AppThemeMode.DARK else AppThemeMode.LIGHT)) {
                                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                                    Text("桌面课表微件", style = MaterialTheme.typography.titleMedium)
                                    // Glance resolves its light/dark ColorProviders from Context resources,
                                    // independently of the application's Compose theme configuration.
                                    CompositionLocalProvider(LocalContext provides widgetContext) {
                                        DesktopScheduleWidgetPreview(Modifier.onGloballyPositioned { coordinates ->
                                            measuredHeight.set(coordinates.size.height)
                                            val bounds = coordinates.boundsInWindow()
                                            measuredBounds.set(Rect(bounds.left.roundToInt(), bounds.top.roundToInt(),
                                                bounds.right.roundToInt(), bounds.bottom.roundToInt()))
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                val layoutDeadline = SystemClock.elapsedRealtime() + 10_000
                while (measuredBounds.get() == null && SystemClock.elapsedRealtime() < layoutDeadline) SystemClock.sleep(50)
                for (label in listOf("高等数学", "大学英语", "博北A201", "博南B102")) awaitVisibleLabel(label)
                instrumentation.waitForIdleSync()
                assertEquals("The production preview must remain 176dp tall at the default font size",
                    (176 * context.resources.displayMetrics.density).roundToInt(), measuredHeight.get())
                // Copy directly from the rendered Window surface to preserve all child outline clipping.
                SystemClock.sleep(200)
                val bounds = measuredBounds.get() ?: throw AssertionError("The preview has no window bounds")
                assertTrue("PixelCopy requires a nonempty preview region", !bounds.isEmpty)
                val hardwarePreview = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
                try {
                    val completed = CountDownLatch(1)
                    val copyResult = AtomicInteger(PixelCopy.ERROR_UNKNOWN)
                    scenario.onActivity { activity ->
                        PixelCopy.request(activity.window, Rect(bounds), hardwarePreview, { result ->
                            copyResult.set(result)
                            completed.countDown()
                        }, Handler(Looper.getMainLooper()))
                    }
                    assertTrue("The rendered preview region should be copied", completed.await(10, TimeUnit.SECONDS))
                    assertEquals("PixelCopy should capture the actual widget View surface", PixelCopy.SUCCESS, copyResult.get())
                    val inset = (16 * context.resources.displayMetrics.density).roundToInt()
                    val backgroundColor = hardwarePreview.getPixel(hardwarePreview.width / 2,
                        (hardwarePreview.height - inset).coerceIn(0, hardwarePreview.height - 1))
                    if (night) {
                        assertTrue("The night widget itself must have a dark background, not only its containing window",
                            Color.luminance(backgroundColor) < 0.2f)
                        val dayColor = dayBackgroundColor ?: throw AssertionError("Day preview must be captured first")
                        assertTrue("Day and night hardware previews must have different widget background colors",
                            backgroundColor != dayColor && Color.luminance(dayColor) - Color.luminance(backgroundColor) > 0.5f)
                    } else {
                        assertTrue("The day widget background should match its light desktop style",
                            Color.luminance(backgroundColor) > 0.8f)
                        dayBackgroundColor = backgroundColor
                    }
                    val filename = if (night) "widget-preview-hardware-night.png" else "widget-preview-hardware.png"
                    File(context.getExternalFilesDir(null), filename).outputStream().use {
                        assertTrue(hardwarePreview.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { hardwarePreview.recycle() }
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                    ?: throw AssertionError("The preview window did not produce a screenshot")
                try {
                    val filename = if (night) "widget-preview-window-night.png" else "widget-preview-window.png"
                    File(context.getExternalFilesDir(null), filename).outputStream().use {
                        assertTrue("The full preview window PNG should be written",
                            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { screenshot.recycle() }
            }
        }
    }

    @Test
    fun actualWidgetPreviewFitsCompactBoundsAndHasNoRefreshOrLaunchActions() = runBlocking {
        for (night in listOf(false, true)) {
            val context = previewContext(night)
            val remoteViews = GlanceRemoteViews().compose(context, DpSize(340.dp, 176.dp)) {
                ScheduleWidgetPreviewContent()
            }.remoteViews
            instrumentation.runOnMainSync {
                val host = RoundedPreviewHost(context)
                host.addView(remoteViews.apply(context, host), FrameLayout.LayoutParams(-1, -1))
                layout(host, 340, 176)
                val texts = descendants(host).filterIsInstance<TextView>()
                for (expected in listOf("还剩 2 节", "刷新", "高等数学", "大学英语", "博北A201", "博南B102")) {
                    val label = findLabel(texts, expected)
                    assertVisibleInside(host, label)
                }
                assertTrue(texts.any { it.text.toString().startsWith("获取于 ") })
                assertFalse("Example content must not trigger a real refresh or navigation",
                    descendants(host).any { it.hasOnClickListeners() })
                val output = File(context.getExternalFilesDir(null),
                    if (night) "widget-preview-night.png" else "widget-preview.png")
                val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
                try {
                    host.draw(Canvas(bitmap))
                    assertEquals("The real widget's rounded outline must be retained in the PNG", 0,
                        Color.alpha(bitmap.getPixel(0, 0)))
                    output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test
    fun launcherPreviewLayoutsKeepBothCoursesVisibleAtNarrowAndWideSizes() {
        for (night in listOf(false, true)) for (width in listOf(280, 340)) {
            val context = previewContext(night)
            instrumentation.runOnMainSync {
                for (layoutId in listOf(R.layout.schedule_widget_preview, R.layout.schedule_adaptive_widget_preview)) {
                    val host = FrameLayout(context)
                    val content = LayoutInflater.from(context).inflate(layoutId, host, false)
                    host.addView(content)
                    layout(host, width, 176)
                    val texts = descendants(host).filterIsInstance<TextView>()
                    for (labelId in listOf(R.string.schedule_widget_preview_course_one,
                        R.string.schedule_widget_preview_course_two, R.string.schedule_widget_preview_detail_one,
                        R.string.schedule_widget_preview_detail_two)) {
                        assertVisibleInside(host, findLabel(texts, context.getString(labelId)))
                    }
                }
            }
        }
    }

    @Test
    fun largeFontPreviewsKeepAllLabelsInsideTheBoundedHeight() = runBlocking {
        for (fontScale in listOf(1.5f, 2f)) {
            val context = previewContext(night = false, fontScale = fontScale)
            val height = (176f * fontScale.coerceIn(1f, 1.5f)).roundToInt()
            val remoteViews = GlanceRemoteViews().compose(context, DpSize(340.dp, height.dp)) {
                ScheduleWidgetPreviewContent()
            }.remoteViews
            instrumentation.runOnMainSync {
                val host = FrameLayout(context)
                host.addView(remoteViews.apply(context, host), FrameLayout.LayoutParams(-1, -1))
                layout(host, 340, height)
                assertAllTextFits(host, "actual Glance, fontScale=$fontScale")
                for (layoutId in listOf(R.layout.schedule_widget_preview, R.layout.schedule_adaptive_widget_preview)) {
                    val xmlHost = FrameLayout(context)
                    xmlHost.addView(LayoutInflater.from(context).inflate(layoutId, xmlHost, false))
                    layout(xmlHost, 340, height)
                    assertAllTextFits(xmlHost, "${context.resources.getResourceEntryName(layoutId)}, fontScale=$fontScale")
                }
            }
        }
    }

    private fun previewContext(night: Boolean, fontScale: Float = 1f): android.content.Context {
        val context = instrumentation.targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            this.fontScale = fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return context.createConfigurationContext(configuration)
    }

    private fun awaitVisibleLabel(expected: String) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val nodes = root?.findAccessibilityNodeInfosByText(expected).orEmpty()
            val visible = nodes.any { it.isVisibleToUser }
            nodes.forEach { it.recycle() }
            root?.recycle()
            if (visible) return
            SystemClock.sleep(100)
        }
        throw AssertionError("The production preview window did not show <$expected> after asynchronous Glance rendering")
    }

    private fun findLabel(texts: List<TextView>, expected: String): TextView =
        texts.singleOrNull { it.text.toString() == expected }
            ?: throw AssertionError("Expected exactly one label <$expected>; actual labels: " +
                texts.joinToString { "<${it.text}>" })

    private fun assertAllTextFits(host: ViewGroup, description: String) {
        val texts = descendants(host).filterIsInstance<TextView>().filter { it.text.isNotBlank() }
        assertTrue("$description should contain both example courses", texts.any { it.text.toString() == "大学英语" })
        for (text in texts) {
            assertVisibleInside(host, text, description)
            val layout = text.layout ?: throw AssertionError("$description has no text layout for <${text.text}>")
            assertTrue("$description truncates <${text.text}>",
                (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })
        }
    }

    private fun layout(view: View, widthDp: Int, heightDp: Int) {
        val density = view.resources.displayMetrics.density
        val width = (widthDp * density + 0.5f).toInt()
        val height = (heightDp * density + 0.5f).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        assertEquals("Preview height must stay compact", height, view.height)
    }

    private fun assertVisibleInside(host: ViewGroup, child: View, description: String = "preview") {
        val bounds = Rect()
        child.getDrawingRect(bounds)
        host.offsetDescendantRectToMyCoords(child, bounds)
        val label = (child as? TextView)?.text?.toString().orEmpty()
        assertTrue("$description label <$label> should have a visible measured size", bounds.width() > 0 && bounds.height() > 0)
        assertTrue("$description label <$label> extends past the compact preview: $bounds / ${host.width}x${host.height}",
            bounds.left >= 0 && bounds.top >= 0 && bounds.right <= host.width && bounds.bottom <= host.height)
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }

    /** Software Canvas ignores clipToOutline, so this host applies the same 28dp outline while drawing. */
    private class RoundedPreviewHost(context: android.content.Context) : FrameLayout(context) {
        override fun dispatchDraw(canvas: Canvas) {
            val radius = 28 * resources.displayMetrics.density
            val outline = Path().apply {
                addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW)
            }
            val save = canvas.save()
            canvas.clipPath(outline)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(save)
        }
    }
}
