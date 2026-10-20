package com.ahu.ahutong.ui.plugin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import androidx.annotation.MainThread
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ahu.ahutong.core.plugin.PluginCameraService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * 宿主相机服务实现（CameraX）。
 *
 * - 权限：宿主持有 CAMERA 权限；插件经 requestPermission 触发系统弹窗，
 *   launcher 在 MainActivity.onCreate 注册（RESUMED 前）并经 [PluginHostLauncherHub] 传递。
 *   永久拒绝后系统不再弹窗（回调 false，由插件 UI 引导去设置）。
 * - 线程模型：analyzer 在相机分析线程回调降采样 Bitmap（短边 ≤ 480px，KEEP_ONLY_LATEST）；
 *   capture 主线程回调 EXIF 旋转校正后的 Bitmap。
 * - 生命周期：close() 解绑全部相机用例（相机指示灯随之熄灭）；重复 startSession 先解绑旧会话。
 */
class PluginCameraServiceImpl(
    private val context: Context
) : PluginCameraService {

    private var activeSession: CameraSessionImpl? = null

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    override fun requestPermission(callback: com.ahu.ahutong.core.plugin.PluginBooleanCallback) {
        PluginHostLauncherHub.requestCameraPermission { granted -> callback.onResult(granted) }
    }

    override fun createPreviewView(): View = PreviewView(context).apply {
        // 即拍即用：插件拿它嵌 AndroidView
        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
    }

    override fun startSession(
        previewView: View,
        analyzer: com.ahu.ahutong.core.plugin.PluginBitmapConsumer?
    ): PluginCameraService.CameraSession {
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider((previewView as PreviewView).surfaceProvider)
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        val imageAnalysis = analyzer?.let { consumer ->
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(cameraExecutor) { proxy ->
                        try {
                            val bitmap = proxy.toBitmap()?.downsample(ANALYSIS_SHORT_SIDE)
                            if (bitmap != null) consumer.onBitmap(bitmap)
                        } finally {
                            proxy.close()
                        }
                    }
                }
        }

        activeSession?.close()
        val session = CameraSessionImpl(
            context, PluginHostLauncherHub.activity as LifecycleOwner,
            preview, imageCapture, imageAnalysis, cameraExecutor
        )
        activeSession = session
        val provider = ProcessCameraProvider.getInstance(context)
        provider.addListener({
            // This listener runs only after completion; get() cannot block the UI.
            runCatching { provider.get() }.fold(session::bind, session::fail)
        }, ContextCompat.getMainExecutor(context))
        return session
    }

    companion object {
        private const val TAG = "PluginCameraService"
        private const val ANALYSIS_SHORT_SIDE = 480
        private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    }
}

/* ---------------- 会话实现（独立文件可见性：package 内） ---------------- */

internal class CameraSessionImpl(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val preview: Preview,
    private val imageCapture: ImageCapture,
    private val imageAnalysis: ImageAnalysis?,
    private val imageExecutor: ExecutorService
) : PluginCameraService.CameraSession {
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private var closed = false
    private var torch = false
    private var failure: Throwable? = null

    fun bind(provider: ProcessCameraProvider) {
        if (closed) return
        try {
            camera = provider.bindToLifecycle(
                owner, CameraSelector.DEFAULT_BACK_CAMERA,
                *listOfNotNull(preview, imageCapture, imageAnalysis).toTypedArray()
            )
            this.provider = provider
            setTorch(torch)
        } catch (error: Exception) {
            fail(error)
        }
    }

    fun fail(error: Throwable) {
        failure = error
        Log.w(TAG, "相机初始化失败", error)
    }

    override fun capture(callback: com.ahu.ahutong.core.plugin.PluginBitmapCallback) {
        val main = ContextCompat.getMainExecutor(context)
        if (closed || camera == null) {
            val error = failure ?: IllegalStateException(if (closed) "相机会话已关闭" else "相机正在初始化，请稍后重试")
            main.execute { callback.onError(error.message ?: "相机不可用") }
            return
        }
        imageCapture.takePicture(
            imageExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()?.rotate(image.imageInfo.rotationDegrees)
                        if (bitmap != null) {
                            main.execute { callback.onSuccess(bitmap) }
                        } else {
                            main.execute { callback.onError("拍照解码失败") }
                        }
                    } catch (t: Throwable) {
                        main.execute { callback.onError(t.message ?: t.javaClass.simpleName) }
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.w(TAG, "拍照失败", exception)
                    main.execute { callback.onError(exception.message ?: "拍照失败") }
                }
            }
        )
    }

    override fun setTorch(on: Boolean) {
        torch = on
        runCatching { camera?.cameraControl?.enableTorch(on) }
    }

    override fun close() {
        closed = true
        runCatching {
            imageAnalysis?.clearAnalyzer()
            provider?.unbind(*listOfNotNull(preview, imageCapture, imageAnalysis).toTypedArray())
        }
        camera = null
        provider = null
    }

    private fun Bitmap?.rotate(degrees: Int): Bitmap? {
        if (this == null || degrees == 0) return this
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    companion object {
        private const val TAG = "PluginCameraService"
    }
}

private fun Bitmap?.downsample(shortSide: Int): Bitmap? {
    if (this == null) return null
    if (minOf(width, height) <= shortSide) return this
    val scale = shortSide.toFloat() / minOf(width, height)
    return Bitmap.createScaledBitmap(this, (width * scale).roundToInt(), (height * scale).roundToInt(), true)
}

/**
 * 宿主 Launcher 集线器：MainActivity 在 onCreate（RESUMED 前）注册
 * RequestPermission + PickVisualMedia 两个 launcher 并把引用放进本单例。
 * 插件宿主实现从这里取能力；拿不到（未初始化）即抛出可读异常。
 */
object PluginHostLauncherHub {
    @Volatile private var requestCameraPermissionImpl: (((Boolean) -> Unit) -> Unit)? = null
    @Volatile private var pickImageLauncherImpl: (((Uri?) -> Unit) -> Unit)? = null
    @Volatile internal var activity: Activity? = null

    fun attach(
        activity: Activity,
        requestCameraPermission: (onResult: (granted: Boolean) -> Unit) -> Unit,
        pickImageLauncher: (onResult: (uri: Uri?) -> Unit) -> Unit
    ) {
        this.activity = activity
        this.requestCameraPermissionImpl = requestCameraPermission
        this.pickImageLauncherImpl = pickImageLauncher
    }

    fun detach() {
        requestCameraPermissionImpl = null
        pickImageLauncherImpl = null
        activity = null
    }

    fun requestCameraPermission(onResult: (granted: Boolean) -> Unit) {
        val impl = requestCameraPermissionImpl
            ?: throw IllegalStateException("宿主相机权限通道未初始化（Activity 已销毁？）")
        impl(onResult)
    }

    fun pickImage(onResult: (uri: Uri?) -> Unit) {
        val impl = pickImageLauncherImpl
            ?: throw IllegalStateException("宿主照片选择器通道未初始化（Activity 已销毁？）")
        impl(onResult)
    }
}

/** 从任意 Context 沿 ContextWrapper 链剥出 Activity（防御性）。 */
@MainThread
internal fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
