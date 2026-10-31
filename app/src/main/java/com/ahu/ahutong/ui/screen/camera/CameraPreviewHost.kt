package com.ahu.ahutong.ui.screen.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.util.Size
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import com.ahu.ahutong.ui.screen.scan.QrAnalyzer
import com.ahu.ahutong.ui.screen.scan.QrDetection
import com.ahu.ahutong.ui.screen.scan.QrCameraSession
import com.ahu.ahutong.ui.screen.scan.ScannerZoomState
import com.ahu.ahutong.ui.screen.scan.freezeQrFrame
import java.util.concurrent.Executors

/**
 * 取景主机：改用 CameraX 的 `LifecycleCameraController`。
 *
 * 这是相机类应用的官方做法：**双指捏合缩放、点击对焦、viewport 裁剪**都由 controller 内部处理，
 * 不需要再和 `PreviewView` 抢触摸事件（之前 Compose pointerInput / OnTouchListener 都拿不到稳定事件）。
 * 分析帧同样由 controller 按 PreviewView 的 viewport 裁剪，冻结帧与候选标记才能对得上位置。
 */
@Composable
@SuppressLint("ClickableViewAccessibility") // Passive touch observer; PreviewView handles clicks and accessibility.
internal fun CameraPreviewHost(
    mode: CameraMode,
    scanGeneration: Int,
    keepScanning: Boolean,
    manualZoomGeneration: Int,
    manualZoomRatio: Float,
    onController: (LifecycleCameraController) -> Unit,
    onZoom: (ScannerZoomState) -> Unit,
    onError: () -> Unit,
    onScanFrame: (List<QrDetection>, Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnController by rememberUpdatedState(onController)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnScanFrame by rememberUpdatedState(onScanFrame)

    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            // 官方内置捏合缩放：内部用 ScaleGestureDetector 接在预览视图上
            setPinchToZoomEnabled(true)
            // 分析与原扫码同档分辨率，识别速度/准确率才一致
            setImageAnalysisResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 960),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            setImageAnalysisBackpressureStrategy(
                androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            )
        }
    }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            keepScreenOn = true
        }
    }
    val executor = remember(lifecycleOwner) {
        Executors.newSingleThreadExecutor { r -> Thread(r, "qr-analysis") }
    }
    var assistSession by remember { mutableStateOf<QrCameraSession?>(null) }

    DisposableEffect(lifecycleOwner, controller) {
        runCatching {
            controller.bindToLifecycle(lifecycleOwner)
            currentOnController(controller)
        }.onFailure { currentOnError() }
        onDispose {
            runCatching { controller.clearImageAnalysisAnalyzer() }
            runCatching { controller.unbind() }
            runCatching { executor.shutdown() }
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = Observer<ZoomState> { state ->
            if (state != null) {
                currentOnZoom(
                    ScannerZoomState(state.zoomRatio, state.minZoomRatio, state.maxZoomRatio)
                )
            }
        }
        controller.zoomState.observe(lifecycleOwner, observer)
        onDispose { controller.zoomState.removeObserver(observer) }
    }

    DisposableEffect(previewView) {
        previewView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_MOVE ||
                event.actionMasked == MotionEvent.ACTION_UP) {
                assistSession?.onManualInteraction(controller.zoomState.value?.zoomRatio ?: 1f)
            }
            false // Keep CameraController's built-in pinch and tap handling.
        }
        onDispose { previewView.setOnTouchListener(null) }
    }

    LaunchedEffect(manualZoomGeneration) {
        if (manualZoomGeneration > 0) assistSession?.onManualInteraction(manualZoomRatio)
    }

    DisposableEffect(mode, scanGeneration, keepScanning, controller, lifecycleOwner) {
        var active = true
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val session = QrCameraSession(mainExecutor)
        if (mode == CameraMode.SCAN) {
            assistSession = session
            val analyzer = QrAnalyzer(
                stopAfterFirst = !keepScanning,
                onFailure = {
                    mainExecutor.execute { if (active) currentOnError() }
                },
                onAssist = { frame ->
                    mainExecutor.execute {
                        if (active && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            val info = controller.cameraInfo
                            val control = controller.cameraControl
                            if (info != null && control != null) {
                                session.apply(frame, info, control, previewView) { ratio ->
                                    runCatching { controller.setZoomRatio(ratio) }
                                }
                            }
                        }
                    }
                },
                onFrame = { found, image ->
                    val snapshot = runCatching { freezeQrFrame(image) }.getOrNull()
                    mainExecutor.execute {
                        if (active && snapshot != null &&
                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        ) {
                            session.close(controller.cameraInfo, controller.cameraControl)
                            currentOnScanFrame(found, snapshot)
                        } else snapshot?.recycle()
                    }
                }
            )
            controller.setImageAnalysisAnalyzer(executor, analyzer)
        } else {
            controller.clearImageAnalysisAnalyzer()
            assistSession = null
        }
        onDispose {
            active = false
            controller.clearImageAnalysisAnalyzer()
            session.close(controller.cameraInfo, controller.cameraControl)
            if (assistSession === session) assistSession = null
        }
    }

    AndroidView(
        factory = {
            previewView.apply { this.controller = controller }
        },
        modifier = modifier.fillMaxSize()
    )
}
