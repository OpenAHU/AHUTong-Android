package com.ahu.ahutong.ui.screen.scan

import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Main-thread controls shared by the unified camera and the standalone scanner. */
internal class QrCameraSession(private val mainExecutor: Executor) {
    private val zoomAssist = QrCameraAssist()
    private val exposureAssist = QrExposureAssist()
    private var manualAt: Long? = null
    private var originalExposure: Int? = null
    private var requestedExposure: Int? = null
    private var exposureRequestOrigin: Int? = null
    private var ownsMetering = false
    private var closed = false

    fun onManualInteraction(ratio: Float) {
        if (closed) return
        val now = System.nanoTime()
        manualAt = now
        ownsMetering = false
        zoomAssist.onManualInteraction(now, ratio)
    }

    fun apply(
        frame: QrAssistFrame,
        info: CameraInfo,
        control: CameraControl,
        preview: PreviewView,
        onZoom: (Float) -> Unit
    ) {
        if (closed) return
        val now = System.nanoTime()
        // Queued results from a slow frame must not move a newer viewfinder.
        if (now - frame.timestampNanos !in 0L..500_000_000L) return
        val zoom = info.zoomState.value ?: return
        val adjustment = zoomAssist.update(now,
            ScannerZoomState(zoom.zoomRatio, zoom.minZoomRatio, zoom.maxZoomRatio),
            frame.target, frame.aspectRatio)
        adjustment.zoomRatio?.let { onZoom(it) }
        adjustment.meteringPoint?.let { point ->
            if (preview.width > 0 && preview.height > 0) {
                val projected = projectQrPoint(point, frame.aspectRatio,
                    preview.width.toFloat(), preview.height.toFloat())
                runCatching {
                    val metering = preview.meteringPointFactory.createPoint(projected.x, projected.y)
                    control.startFocusAndMetering(FocusMeteringAction.Builder(metering)
                        .setAutoCancelDuration(2, TimeUnit.SECONDS).build())
                }.getOrNull()?.let {
                    ownsMetering = true
                    it.observeCompletion()
                }
            }
        }
        if (manualAt?.let { now - it < 3_000_000_000L } == true) return
        val exposure = info.exposureState
        if (!exposure.isExposureCompensationSupported) return
        val index = exposure.exposureCompensationIndex
        val range = exposure.exposureCompensationRange
        exposureAssist.update(now, frame.lighting, index, range.lower, range.upper,
            exposure.exposureCompensationStep.toFloat())?.let { requested ->
            if (originalExposure == null) originalExposure = index
            exposureRequestOrigin = index
            requestedExposure = requested
            runCatching { control.setExposureCompensationIndex(requested) }
                .getOrNull()?.also { it.observeCompletion() }
        }
    }

    fun close(info: CameraInfo?, control: CameraControl?): Float? {
        if (closed) return null
        closed = true
        if (ownsMetering) runCatching { control?.cancelFocusAndMetering() }
            .getOrNull()?.observeCompletion()
        val restoredZoom = info?.zoomState?.value?.let { state ->
            zoomAssist.restorationRatio(ScannerZoomState(state.zoomRatio, state.minZoomRatio, state.maxZoomRatio))
                ?.also { ratio -> runCatching { control?.setZoomRatio(ratio) }.getOrNull()?.observeCompletion() }
        }
        // A newer request cancels an in-flight scan exposure before note capture can inherit it.
        val original = originalExposure ?: return restoredZoom
        val index = info?.exposureState?.exposureCompensationIndex
        if (index == requestedExposure || index == exposureRequestOrigin) {
            runCatching { control?.setExposureCompensationIndex(original) }
                .getOrNull()?.observeCompletion()
        }
        return restoredZoom
    }

    private fun ListenableFuture<*>.observeCompletion() {
        addListener({ runCatching { get() } }, mainExecutor)
    }
}
