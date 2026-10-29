package com.ahu.ahutong.ui.screen.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.widget.Toast
import android.view.View
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraState
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.google.zxing.RGBLuminanceSource
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Result panel text cap: long payloads only need to be recognisable, not fully laid out. */
private const val RESULT_TEXT_LIMIT = 180

private data class ScannerFailure(val title: String, val retryRoute: Boolean = false)
private data class ScannedPhoto(val candidates: List<QrDetection>, val bitmap: Bitmap)

/** Camera results stabilize before confirmation; unsupported QR contents never reach the platform router. */
@Composable
fun ScanScreen(onBack: () -> Unit, onResult: (String) -> QrRouter.Outcome) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnBack by rememberUpdatedState(onBack)
    val gate = remember { ScanResultGate() }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var result by remember { mutableStateOf<String?>(null) }
    var detection by remember { mutableStateOf<QrDetection?>(null) }
    var previewSnapshot by remember { mutableStateOf<Bitmap?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ScannerFailure?>(null) }
    var readingPhoto by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    var routeAttempt by remember { mutableIntStateOf(0) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusRequestId by remember { mutableIntStateOf(0) }
    var candidates by remember { mutableStateOf<List<QrDetection>>(emptyList()) }
    var choosingTarget by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var zoomState by remember { mutableStateOf(ScannerZoomState()) }
    var recognizeAfterNanos by remember { mutableStateOf<Long?>(null) }

    fun resetScan() {
        recognizeAfterNanos = System.nanoTime() + 1_000_000_000L
        gate.reset()
        result = null
        detection = null
        failure = null
        cameraError = false
        confirming = false
        previewSnapshot = null
        candidates = emptyList()
        choosingTarget = false
        focusPoint = null
        cameraAttempt++
    }

    fun acceptResult(found: QrDetection, snapshot: Bitmap? = null) {
        if (gate.accept(found.raw)) {
            torchEnabled = false
            previewSnapshot = snapshot
            result = found.raw
            detection = found
            confirming = true
            failure = null
            routeAttempt++
            // Haptic availability must never determine whether a result can be handled.
            try { haptics.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) { }
        }
    }

    fun freezeCandidates(found: List<QrDetection>, snapshot: Bitmap) {
        if (recognizeAfterNanos?.let { System.nanoTime() < it } == true || found.isEmpty() ||
            !resumed || cameraError || result != null || previewSnapshot != null || pickerOpen || readingPhoto) {
            snapshot.recycle()
            return
        }
        torchEnabled = false
        candidates = found
        choosingTarget = found.size > 1
        if (choosingTarget) {
            previewSnapshot = snapshot
            detection = null
        } else acceptResult(found.single(), snapshot)
    }

    LaunchedEffect(result, routeAttempt) {
        val raw = result ?: return@LaunchedEffect
        delay(260)
        confirming = false
        if (QrRouter.classify(raw) == QrRouter.Kind.Unknown) return@LaunchedEffect
        lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        when (val outcome = QrRouter.dispatch(raw) { _, text -> currentOnResult(text) }) {
            is QrRouter.Outcome.Handed -> {
                outcome.note?.let { note ->
                    try { Toast.makeText(context, note, Toast.LENGTH_SHORT).show() } catch (_: Exception) { }
                }
                try { currentOnBack() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failure = ScannerFailure("无法返回") }
            }
            is QrRouter.Outcome.TargetMissing -> failure = ScannerFailure("未找到${outcome.targetApp}", true)
            is QrRouter.Outcome.Failed -> failure = ScannerFailure("无法打开${outcome.targetApp}", true)
            QrRouter.Outcome.Unrecognized -> Unit
        }
    }
    LaunchedEffect(focusPoint, focusRequestId) { if (focusPoint != null) { delay(700); focusPoint = null } }

    val permissionContract = remember { ActivityResultContracts.RequestPermission() }
    val permissionLauncher = rememberLauncherForActivityResult(permissionContract) {
        permissionRequested = true
        granted = it
    }
    val photoContract = remember { ActivityResultContracts.PickVisualMedia() }
    val photoLauncher = rememberLauncherForActivityResult(photoContract) { uri ->
        pickerOpen = false
        if (uri != null) {
            readingPhoto = true
            scope.launch {
                try {
                    val photo = withContext(Dispatchers.IO) { decodePhoto(context, uri) }
                    if (photo != null && photo.candidates.isNotEmpty()) {
                        lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                        if (photo.candidates.size == 1) acceptResult(photo.candidates.single(), photo.bitmap)
                        else {
                            previewSnapshot = photo.bitmap
                            detection = null
                            candidates = photo.candidates
                            choosingTarget = true
                        }
                    } else failure = ScannerFailure("未识别到二维码")
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failure = ScannerFailure("无法读取图片")
                } finally {
                    readingPhoto = false
                }
            }
        }
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        if (!granted && !permissionRequested) {
            permissionRequested = true
            try { permissionLauncher.launch(Manifest.permission.CAMERA) }
            catch (_: Exception) { failure = ScannerFailure("无法请求相机权限") }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    granted = hasCameraPermission(context)
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    torchEnabled = false
                    if (result == null && previewSnapshot == null) {
                        candidates = emptyList()
                        detection = null
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted && resumed && result == null && previewSnapshot == null && !readingPhoto && !pickerOpen && !cameraError && failure == null) {
            key(cameraAttempt) {
                ScannerCameraPreview(
                    recognizeAfterNanos = recognizeAfterNanos,
                    zoomRatio = zoomRatio,
                    onZoomRatioChange = { zoomRatio = it },
                    onZoomState = { zoomState = it },
                    torchEnabled = torchEnabled,
                    onFlashAvailability = { hasFlash = it },
                    onFrozenFrame = { found, snapshot -> freezeCandidates(found, snapshot) },
                    onFocus = { focusPoint = it; focusRequestId++ },
                    onError = {
                        cameraError = true
                        candidates = emptyList()
                        detection = null
                        torchEnabled = false
                        failure = ScannerFailure("相机暂不可用")
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        previewSnapshot?.takeUnless { it.isRecycled }?.let {
            Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit)
        }
        ScannerFrame(detection, confirming, focusPoint, Modifier.fillMaxSize(), focusRequestId = focusRequestId,
            showIdleFrame = !choosingTarget || result != null)
        if (choosingTarget && result == null && !readingPhoto && !pickerOpen && resumed) {
            ScannerTargetsOverlay(candidates, onSelect = { selected ->
                previewSnapshot?.let { acceptResult(selected, it) }
            })
        }

        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White)
            }
            Text("扫一扫", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }

        if (readingPhoto || (result == null && previewSnapshot == null && (!granted || cameraError))) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when {
                    readingPhoto -> CircularProgressIndicator(color = Color.White)
                    !granted -> {
                        Text("需要相机权限", color = Color.White)
                        val openSettings = permissionRequested && activity != null &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
                        AppButton(onClick = {
                            try {
                                if (openSettings) context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                ) else permissionLauncher.launch(Manifest.permission.CAMERA)
                            } catch (_: Exception) { failure = ScannerFailure("无法打开设置") }
                        }) { Text(if (openSettings) "前往设置" else "授予权限") }
                    }
                    else -> {
                        Text("相机暂不可用", color = Color.White)
                        AppButton(onClick = ::resetScan) { Text("重试") }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!confirming) result?.let { raw ->
                // 冻结帧（全屏照片）上不再叠采样玻璃：Android 17 / PKX110 上
                // "照片层 + drawBackdrop 模糊" 会在 RenderThread 的 HWUI LinearAllocator 里
                // 原生崩溃（关玻璃效果、换主题即不复现）。这里改用实色卡片，零 RenderEffect。
                // 文案也做截断：学习通 enc / 长 URL 动辄上千字符，没必要整段参与排版。
                val displayRaw = remember(raw) {
                    if (raw.length <= RESULT_TEXT_LIMIT) raw else raw.take(RESULT_TEXT_LIMIT) + "…"
                }
                Surface(
                    shape = SmoothRoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (QrRouter.classify(raw) == QrRouter.Kind.Unknown) "不支持此二维码" else "扫描结果",
                            style = MaterialTheme.typography.titleMedium)
                        Text(displayRaw, maxLines = 4, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppButton(
                                onClick = {
                                    try { clipboard.setText(AnnotatedString(raw)) }
                                    catch (_: Exception) { failure = ScannerFailure("无法复制") }
                                },
                                variant = AppButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            ) { Text("复制") }
                            AppButton(onClick = ::resetScan, modifier = Modifier.weight(1f)) { Text("继续扫描") }
                        }
                    }
                }
            }
            if (result == null) {
                if (granted && previewSnapshot == null && !cameraError && !readingPhoto && !pickerOpen) {
                    ScannerZoomControls(zoomState, onZoomRatioChange = { zoomRatio = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    ScannerControl(
                        icon = Icons.Rounded.PhotoLibrary,
                        label = "相册",
                        enabled = !readingPhoto && !pickerOpen,
                        onClick = {
                            torchEnabled = false
                            pickerOpen = true
                            try { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            catch (_: Exception) { pickerOpen = false; failure = ScannerFailure("无法打开相册") }
                        }
                    )
                    if (previewSnapshot != null) {
                        ScannerControl(icon = Icons.Rounded.Refresh, label = "重扫", onClick = ::resetScan)
                    } else if (granted && hasFlash && !cameraError) {
                        ScannerControl(
                            icon = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            label = "闪光灯",
                            selected = torchEnabled,
                            enabled = !readingPhoto && !pickerOpen,
                            onClick = { torchEnabled = !torchEnabled }
                        )
                    }
                }
            }
        }
    }
    failure?.let { error ->
        AppDialog(
            title = error.title,
            onDismiss = { failure = null },
            actions = if (error.retryRoute) listOf(
                AppDialogAction("继续扫描", onClick = ::resetScan),
                AppDialogAction("重试", onClick = { failure = null; routeAttempt++ },
                    style = AppDialogActionStyle.Primary)
            ) else if (cameraError) listOf(
                AppDialogAction("取消", onClick = { failure = null }),
                AppDialogAction("重试", onClick = ::resetScan, style = AppDialogActionStyle.Primary)
            ) else listOf(AppDialogAction("知道了", onClick = { failure = null },
                style = AppDialogActionStyle.Primary))
        )
    }
}

@Composable
private fun ScannerControl(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(60.dp).background(
                if (selected) Color.White else Color.White.copy(alpha = 0.16f), CircleShape
            )
        ) {
            Icon(icon, label, tint = if (selected) Color.Black else Color.White.copy(alpha = if (enabled) 1f else 0.4f))
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ScannerFrame(
    detection: QrDetection?,
    confirming: Boolean,
    focusPoint: Offset?,
    modifier: Modifier = Modifier,
    focusRequestId: Int = 0,
    showIdleFrame: Boolean = true
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val outlined = detection?.takeIf { it.corners.size == 4 }
    val found by animateFloatAsState(if (outlined != null) 1f else 0f, tween(260), label = "qr-outline")
    val emphasis by animateFloatAsState(if (confirming) 1.08f else 1f, spring(stiffness = 500f), label = "qr-confirm")
    val pulse = rememberInfiniteTransition(label = "qr-idle")
    val frameAlpha by pulse.animateFloat(0.78f, 1f,
        infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "qr-frame-alpha")
    val focusProgress = remember { Animatable(1f) }
    LaunchedEffect(focusPoint, focusRequestId) {
        focusProgress.snapTo(if (focusPoint == null) 1f else 0f)
        if (focusPoint != null) focusProgress.animateTo(1f, tween(520, easing = LinearEasing))
    }
    val w = viewport.width.toFloat().coerceAtLeast(1f)
    val h = viewport.height.toFloat().coerceAtLeast(1f)
    val side = minOf(w * 0.72f, h * 0.42f)
    val left = (w - side) / 2f
    val top = (h - side) / 2f - h * 0.06f
    val default = listOf(Offset(left, top), Offset(left + side, top),
        Offset(left + side, top + side), Offset(left, top + side))
    val target = outlined?.corners?.map { point ->
        val projected = projectQrPoint(point, outlined.aspectRatio, w, h)
        Offset(projected.x, projected.y)
    } ?: default
    val corners = target.mapIndexed { index, point ->
        val x by animateFloatAsState(point.x, spring(stiffness = 650f), label = "qr-x-$index")
        val y by animateFloatAsState(point.y, spring(stiffness = 650f), label = "qr-y-$index")
        Offset(x, y)
    }
    Canvas(modifier.onSizeChanged { viewport = it }.semantics {
        contentDescription = if (outlined != null) "已识别二维码" else "扫码取景框"
    }) {
        val scrim = Color.Black.copy(alpha = 0.48f)
        val center = corners.reduce { total, point -> total + point } / 4f
        val outlineScale = (0.7f + 0.3f * found) * emphasis
        // The aperture and coloured corners must follow exactly the same focus motion.
        val focusCorners = corners.mapIndexed { index, point ->
            val targetPoint = center + (point - center) * outlineScale
            default[index] + (targetPoint - default[index]) * found
        }
        val cutout = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
            if (found <= 0.001f) {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                    androidx.compose.ui.geometry.Rect(left, top, left + side, top + side),
                    androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())))
            } else {
                moveTo(focusCorners[0].x, focusCorners[0].y)
                focusCorners.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
        }
        if (showIdleFrame || found > 0.001f) drawPath(cutout, scrim)
        else drawRect(Color.Black.copy(alpha = 0.18f))
        fun outline(points: List<Offset>, color: Color, amount: Float = 1f) {
            val center = points.reduce { total, point -> total + point } / points.size.toFloat()
            val scaled = points.map { center + (it - center) * amount }
            val path = Path()
            scaled.forEachIndexed { i, point ->
                val previous = scaled[(i + 3) % 4]
                val next = scaled[(i + 1) % 4]
                path.moveTo(point.x + (previous.x - point.x) * 0.18f, point.y + (previous.y - point.y) * 0.18f)
                path.lineTo(point.x, point.y)
                path.lineTo(point.x + (next.x - point.x) * 0.18f, point.y + (next.y - point.y) * 0.18f)
            }
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (showIdleFrame) outline(default, Color.White.copy(alpha = frameAlpha * (1f - found)))
        if (found > 0.01f) outline(focusCorners, Color(0xFFFFDE07).copy(alpha = found))
        focusPoint?.let { point ->
            val progress = focusProgress.value
            val contraction = FastOutSlowInEasing.transform((progress * 520f / 200f).coerceIn(0f, 1f))
            val outerAlpha = 1f - ((progress * 520f - 350f) / 170f).coerceIn(0f, 1f)
            val innerAlpha = 0.5f * (1f - ((progress * 520f - 200f) / 150f).coerceIn(0f, 1f))
            drawCircle(Color.White.copy(alpha = outerAlpha), 30.dp.toPx() * (2f - contraction), point,
                style = Stroke(1.5f.dp.toPx()))
            drawCircle(Color.White.copy(alpha = innerAlpha), 30.dp.toPx() * contraction, point,
                style = Stroke(1.5f.dp.toPx()))
        }
    }
}

@Composable
private fun ScannerCameraPreview(
    zoomRatio: Float,
    onZoomRatioChange: (Float) -> Unit,
    onZoomState: (ScannerZoomState) -> Unit,
    torchEnabled: Boolean,
    onFlashAvailability: (Boolean) -> Unit,
    onFrozenFrame: (List<QrDetection>, Bitmap) -> Unit,
    onFocus: (Offset) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
    recognizeAfterNanos: Long? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError by rememberUpdatedState(onError)
    val currentOnFrozenFrame by rememberUpdatedState(onFrozenFrame)
    val currentOnFocus by rememberUpdatedState(onFocus)
    val currentOnFlashAvailability by rememberUpdatedState(onFlashAvailability)
    val currentZoomRatio by rememberUpdatedState(zoomRatio)
    val currentOnZoomRatioChange by rememberUpdatedState(onZoomRatioChange)
    val currentOnZoomState by rememberUpdatedState(onZoomState)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraBindingId by remember { mutableIntStateOf(0) }
    var zoomRequestId by remember { mutableIntStateOf(0) }
    var appliedZoom by remember(camera, cameraBindingId) { mutableStateOf(1f) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            keepScreenOn = true
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "qr-analysis") }
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = try { ProcessCameraProvider.getInstance(context) }
            catch (_: Exception) { currentOnError(); null }
        @Suppress("DEPRECATION")
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 960))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        @Suppress("DEPRECATION")
        val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val stateObserver = Observer<CameraState> { state ->
            if (!disposed && state.error != null) currentOnError()
        }
        val zoomObserver = Observer<ZoomState> { state ->
            if (!disposed && state != null) {
                currentOnZoomState(ScannerZoomState(state.zoomRatio, state.minZoomRatio, state.maxZoomRatio))
            }
        }
        val analyzer = QrAnalyzer(
            onFailure = { mainExecutor.execute { if (!disposed) currentOnError() } },
            recognizeAfterNanos = recognizeAfterNanos,
            onFrame = { found, image ->
                val snapshot = freezeQrFrame(image)
                mainExecutor.execute {
                    if (!disposed && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        currentOnFrozenFrame(found, snapshot)
                    } else snapshot.recycle()
                }
            }
        )
        var boundSize = IntSize.Zero
        fun bindWhenReady() {
            val cameraProvider = provider ?: return
            val viewport = previewView.viewPort ?: return
            val size = IntSize(previewView.width, previewView.height)
            if (disposed || size.width <= 0 || size.height <= 0 || size == boundSize) return
            try {
                camera?.cameraInfo?.cameraState?.removeObserver(stateObserver)
                camera?.cameraInfo?.zoomState?.removeObserver(zoomObserver)
                if (boundSize != IntSize.Zero) cameraProvider.unbind(preview, analysis)
                val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                preview.targetRotation = rotation
                analysis.targetRotation = rotation
                val useCases = UseCaseGroup.Builder().setViewPort(viewport)
                    .addUseCase(preview).addUseCase(analysis).build()
                analysis.setAnalyzer(executor, analyzer)
                val boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, useCases
                )
                boundSize = size
                cameraBindingId++
                camera = boundCamera
                currentOnFlashAvailability(boundCamera.cameraInfo.hasFlashUnit())
                boundCamera.cameraInfo.cameraState.observe(lifecycleOwner, stateObserver)
                boundCamera.cameraInfo.zoomState.observe(lifecycleOwner, zoomObserver)
            } catch (_: Exception) {
                if (!disposed) currentOnError()
            }
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> bindWhenReady() }
        previewView.addOnLayoutChangeListener(layoutListener)
        providerFuture?.addListener({
            if (!disposed) {
                try {
                    provider = providerFuture.get()
                    bindWhenReady()
                } catch (_: Exception) {
                    if (!disposed) currentOnError()
                }
            }
        }, mainExecutor)
        onDispose {
            disposed = true
            previewView.removeOnLayoutChangeListener(layoutListener)
            camera?.cameraInfo?.cameraState?.removeObserver(stateObserver)
            camera?.cameraInfo?.zoomState?.removeObserver(zoomObserver)
            try { camera?.cameraControl?.enableTorch(false) } catch (_: Exception) { }
            camera = null
            analysis.clearAnalyzer()
            try { provider?.unbind(preview, analysis) } catch (_: Exception) { }
            previewView.keepScreenOn = false
            executor.shutdown()
        }
    }
    LaunchedEffect(camera, cameraBindingId, zoomRatio) {
        val activeCamera = camera ?: return@LaunchedEffect
        val bindingId = cameraBindingId
        val state = activeCamera.cameraInfo.zoomState.value ?: return@LaunchedEffect
        val bounds = ScannerZoomState(state.zoomRatio, state.minZoomRatio, state.maxZoomRatio)
        val requested = bounds.clamp(zoomRatio)
        val requestId = ++zoomRequestId
        if (requested != zoomRatio) currentOnZoomRatioChange(requested)
        fun restoreZoom() {
            if (camera === activeCamera && cameraBindingId == bindingId && requestId == zoomRequestId &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val restored = bounds.clamp(appliedZoom)
                currentOnZoomState(bounds.copy(ratio = restored))
                currentOnZoomRatioChange(restored)
            }
        }
        try {
            val future = activeCamera.cameraControl.setZoomRatio(requested)
            future.addListener({
                if (camera !== activeCamera || cameraBindingId != bindingId || requestId != zoomRequestId) return@addListener
                try {
                    future.get()
                    appliedZoom = requested
                } catch (_: java.util.concurrent.CancellationException) {
                    // CameraX supersedes in-flight requests during pinch gestures or unbinding.
                } catch (error: Exception) {
                    if (error.cause !is CameraControl.OperationCanceledException &&
                        error !is CameraControl.OperationCanceledException) restoreZoom()
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (_: CameraControl.OperationCanceledException) {
        } catch (_: Exception) {
            restoreZoom()
        }
    }
    LaunchedEffect(camera, torchEnabled) {
        try {
            camera?.takeIf { it.cameraInfo.hasFlashUnit() }?.let { activeCamera ->
                val future = activeCamera.cameraControl.enableTorch(torchEnabled)
                future.addListener({
                    try { future.get() }
                    catch (_: java.util.concurrent.CancellationException) { }
                    catch (_: Exception) { if (camera === activeCamera) currentOnError() }
                }, ContextCompat.getMainExecutor(context))
            }
        } catch (_: Exception) { currentOnError() }
    }
    AndroidView(
        factory = { previewView },
        modifier = modifier.pointerInput(camera) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var gestureRatio = currentZoomRatio
                while (true) {
                    val event = awaitPointerEvent()
                    val factor = event.calculateZoom()
                    if (event.changes.count { it.pressed } >= 2 && factor != 1f) {
                        val state = camera?.cameraInfo?.zoomState?.value
                        if (state != null) {
                            val zoom = ScannerZoomState(gestureRatio, state.minZoomRatio, state.maxZoomRatio)
                            gestureRatio = zoom.scaledBy(factor)
                            currentOnZoomRatioChange(gestureRatio)
                            event.changes.forEach { it.consume() }
                        }
                    }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }.pointerInput(camera) {
            detectTapGestures { point ->
                try {
                    currentOnFocus(point)
                    val meteringPoint = previewView.meteringPointFactory.createPoint(point.x, point.y)
                    camera?.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(meteringPoint)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                    )
                } catch (_: Exception) { currentOnError() }
            }
        }
    )
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun decodePhoto(context: Context, uri: Uri): ScannedPhoto? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 2048) sampleSize *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } ?: return null
    try {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val found = QrImageDecoder().decodeCandidates(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))
        if (found.isNotEmpty()) return ScannedPhoto(found, bitmap)
    } catch (error: Exception) {
        bitmap.recycle()
        throw error
    }
    bitmap.recycle()
    return null
}
