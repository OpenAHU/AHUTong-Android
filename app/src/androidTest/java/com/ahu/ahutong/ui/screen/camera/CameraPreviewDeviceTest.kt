package com.ahu.ahutong.ui.screen.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.ahu.ahutong.ui.launchIsolatedTestActivity
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the production preview in an isolated Activity; never changes permissions, settings or accounts. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(TransformExperimental::class)
class CameraPreviewDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test(timeout = 90_000L)
    fun scanPreviewStreamsRefocusesAndReleasesAcrossLifecycle() {
        assumeTrue("Existing camera permission is required; this test never grants it",
            ContextCompat.checkSelfPermission(instrumentation.targetContext, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED)

        val controller = AtomicReference<LifecycleCameraController>()
        val preview = AtomicReference<PreviewView>()
        val cameraInfo = AtomicReference<CameraInfo>()
        val cameraState = AtomicReference<CameraState.Type>()
        val failures = AtomicInteger()
        val stateObserver = Observer<CameraState> { state -> cameraState.set(state?.type) }
        val scenario = launchIsolatedTestActivity()
        var scenarioClosed = false
        var stateObserved = false
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    Box(Modifier.fillMaxSize()) {
                        CameraPreviewHost(
                            mode = CameraMode.SCAN,
                            scanGeneration = 0,
                            keepScanning = true,
                            manualZoomGeneration = 0,
                            manualZoomRatio = 1f,
                            onController = controller::set,
                            onZoom = {},
                            onError = { failures.incrementAndGet() },
                            // Do not retain decoded contents or camera images, or invoke routing.
                            onScanFrame = { _, frozen -> frozen.recycle() }
                        )
                    }
                }
            }
            awaitStreaming(scenario, preview)
            assertEquals("Production camera host reported a preview failure", 0, failures.get())
            scenario.onActivity {
                val info = checkNotNull(controller.get()?.cameraInfo)
                cameraInfo.set(info)
                info.cameraState.observeForever(stateObserver)
                stateObserved = true
            }
            assertEquals(CameraState.Type.OPEN, cameraState.get())

            var focusSupported = false
            val focusFuture = AtomicReference<ListenableFuture<FocusMeteringResult>>()
            scenario.onActivity {
                val view = checkNotNull(preview.get())
                val activeController = checkNotNull(controller.get())
                val point = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(10, TimeUnit.SECONDS).build()
                focusSupported = checkNotNull(activeController.cameraInfo).isFocusMeteringSupported(action)
                if (focusSupported) {
                    focusFuture.set(checkNotNull(activeController.cameraControl).startFocusAndMetering(action))
                }
            }
            var focusCompleted = false
            try {
                if (focusSupported) {
                    assertNotNull("Supported AF request must complete", focusFuture.get().get(10, TimeUnit.SECONDS))
                    focusCompleted = true
                }
            } finally {
                val cancelFuture = AtomicReference<ListenableFuture<Void>>()
                scenario.onActivity {
                    controller.get()?.cameraControl?.let { cancelFuture.set(it.cancelFocusAndMetering()) }
                }
                cancelFuture.get()?.get(5, TimeUnit.SECONDS)
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            awaitCameraClosed(cameraState)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitStreaming(scenario, preview)
            assertEquals("Production preview failed after lifecycle resume", 0, failures.get())
            assertEquals(CameraState.Type.OPEN, cameraState.get())

            scenario.close()
            scenarioClosed = true
            awaitCameraClosed(cameraState)
            instrumentation.runOnMainSync {
                assertNull("Disposed production host must unbind its controller", controller.get().cameraInfo)
            }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "Camera preview streamed before and after lifecycle stop; " +
                    "AF supported=$focusSupported, request completed=$focusCompleted; camera closed after disposal.\n")
            })
        } finally {
            if (!scenarioClosed) scenario.close()
            if (stateObserved) {
                instrumentation.runOnMainSync { cameraInfo.get().cameraState.removeObserver(stateObserver) }
            }
        }
    }

    private fun awaitStreaming(scenario: ActivityScenario<ComponentActivity>, preview: AtomicReference<PreviewView>) {
        val deadline = SystemClock.uptimeMillis() + 15_000L
        var streaming = false
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val view = findPreview(activity.window.decorView)
                if (view != null && view.width > 0 && view.height > 0 &&
                    view.previewStreamState.value == PreviewView.StreamState.STREAMING &&
                    view.outputTransform != null
                ) {
                    val bitmap: Bitmap? = view.bitmap
                    try {
                        if (bitmap != null) {
                            assertTrue("Preview bitmap must have valid dimensions", bitmap.width > 0 && bitmap.height > 0)
                            preview.set(view)
                            streaming = true
                        }
                    } finally { bitmap?.recycle() }
                }
            }
            if (streaming) return
            SystemClock.sleep(80L)
        }
        assertTrue("Production PreviewView did not stream with a transform and bitmap", streaming)
    }

    private fun awaitCameraClosed(state: AtomicReference<CameraState.Type>) {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (state.get() == CameraState.Type.CLOSED) return
            SystemClock.sleep(80L)
        }
        assertEquals("Camera must close when its lifecycle stops or host is disposed", CameraState.Type.CLOSED, state.get())
    }

    private fun findPreview(view: View): PreviewView? {
        if (view is PreviewView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findPreview(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
