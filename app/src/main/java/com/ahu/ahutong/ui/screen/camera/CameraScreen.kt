package com.ahu.ahutong.ui.screen.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.LifecycleCameraController
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import android.os.SystemClock
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.notes.ArchiveTarget
import com.ahu.ahutong.data.notes.NotePhoto
import com.ahu.ahutong.data.notes.NoteSource
import com.ahu.ahutong.data.notes.NoteStore
import com.ahu.ahutong.data.notes.NoteSubject
import com.ahu.ahutong.data.notes.UNFILED_SUBJECT_ID
import com.ahu.ahutong.data.notes.archiveSlotOf
import com.ahu.ahutong.data.notes.archiveSubjectName
import com.ahu.ahutong.data.notes.resolveArchiveTarget
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.screen.scan.QrAnalyzer
import com.ahu.ahutong.ui.screen.scan.QrDetection
import com.ahu.ahutong.ui.screen.scan.QrImageDecoder
import com.ahu.ahutong.ui.screen.scan.QrRouter
import com.ahu.ahutong.ui.screen.scan.ScanResultGate
import com.ahu.ahutong.ui.screen.scan.ScannerTargetsOverlay
import com.ahu.ahutong.ui.screen.scan.ScannerZoomControls
import com.ahu.ahutong.ui.screen.scan.ScannerZoomState
import com.ahu.ahutong.ui.screen.scan.freezeQrFrame
import com.ahu.ahutong.ui.state.ScheduleViewModel
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 相机页元素的反向旋转角度（连续值，便于跨象限走最短路径），每个元素绕自身中心旋转。 */
internal val LocalCameraRotation = staticCompositionLocalOf { 0f }

/** 给单个元素套上反向旋转；带非线性缓动，布局槽位与点击命中区域保持原样。 */
@Composable
internal fun Modifier.cameraRotated(): Modifier {
    val target = LocalCameraRotation.current
    val angle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 280,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "camera-element-rotation"
    )
    return if (angle == 0f) this else graphicsLayer { rotationZ = angle }
}

private val RATIO_4_3 = androidx.camera.core.AspectRatio.RATIO_4_3
private const val MAX_PHOTO_EDGE = 2560
private const val PHOTO_QUALITY = 92
private const val SHUTTER_SIZE = 64
private const val CONTROL_SLOT = 44
private val MODE_ITEM_WIDTH = 58.dp
private val MODE_ITEM_GAP = 8.dp
private val MODE_ITEM_HEIGHT = 30.dp

/**
 * 聚合相机：扫码 + 笔记两个模式共用一块取景。
 *
 * - 扫码：沿用既有 `QrAnalyzer` / `QrImageDecoder` / `QrRouter`，识别即路由；仅界面换新。
 * - 笔记：快门拍照 → 按课表当前课程自动归档（无课表/多门命中落未归档）→ 本地私有存储。
 * - 朝向：窗口锁竖屏，元素各自绕自身中心反向旋转；预览与照片都按物理朝向摆正。
 */
@Composable
fun CameraScreen(
    scheduleViewModel: ScheduleViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var mode by remember { mutableStateOf(CameraModeStore.lastMode()) }
    var rotation by remember { mutableIntStateOf(0) }
    var showGallery by remember { mutableStateOf(false) }
    var noteGalleryIntroSeen by remember { mutableStateOf(CameraModeStore.hasSeenNoteGalleryIntro()) }
    var cameraBounds by remember { mutableStateOf(Rect.Zero) }
    var galleryButtonBounds by remember { mutableStateOf(Rect.Zero) }

    var camera by remember { mutableStateOf<LifecycleCameraController?>(null) }
    var hasFlash by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    var zoomState by remember { mutableStateOf(ScannerZoomState()) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var manualZoomGeneration by remember { mutableIntStateOf(0) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    // 扫码状态
    var result by remember { mutableStateOf<String?>(null) }
    var detection by remember { mutableStateOf<QrDetection?>(null) }
    var snapshot by remember { mutableStateOf<Bitmap?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<QrDetection>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var routeAttempt by remember { mutableIntStateOf(0) }
    var scanGeneration by remember { mutableIntStateOf(0) }
    val gate = remember { ScanResultGate() }


    // 笔记状态
    var subjects by remember { mutableStateOf<List<NoteSubject>>(emptyList()) }
    var manualSubjectId by remember { mutableStateOf<String?>(null) }
    var autoTarget by remember { mutableStateOf<ArchiveTarget?>(null) }
    var lastPhoto by remember { mutableStateOf<NotePhoto?>(null) }
    var taking by remember { mutableStateOf(false) }
    var showSubjectPicker by remember { mutableStateOf(false) }
    var showSubjectManager by remember { mutableStateOf(false) }
    var noteRefreshTick by remember { mutableIntStateOf(0) }

    val granted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = decodeQrFromUri(context, uri)
                if (decoded == null) {
                    failure = "没在这张图里找到二维码"
                } else {
                    val (found, bitmap) = decoded
                    if (found.size == 1) {
                        if (gate.accept(found.first().raw)) {
                            snapshot = bitmap
                            result = found.first().raw
                            detection = found.first()
                            confirming = true
                        } else bitmap.recycle()
                    } else {
                        snapshot = bitmap
                        detection = null
                        candidates = found
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted.value = it }

    val showNoteGalleryIntro = granted.value && mode == CameraMode.NOTE && !noteGalleryIntroSeen &&
        !showGallery && galleryButtonBounds.width > 0 && galleryButtonBounds.height > 0

    fun finishNoteGalleryIntro() {
        if (!noteGalleryIntroSeen) {
            CameraModeStore.markNoteGalleryIntroSeen()
            noteGalleryIntroSeen = true
        }
    }

    // 物理朝向：0/90/180/270，带迟滞
    val orientationListener = remember(context) {
        object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                rotation = quantizeCameraRotation(orientation.toFloat(), rotation)
            }
        }
    }
    DisposableEffect(Unit) {
        orientationListener.enable()
        onDispose { orientationListener.disable() }
    }

    LaunchedEffect(Unit) {
        if (!granted.value) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 科目与自动归档目标
    LaunchedEffect(mode, showGallery, noteRefreshTick) {
        subjects = withContext(Dispatchers.IO) { NoteStore.subjects() }
        lastPhoto = withContext(Dispatchers.IO) { NoteStore.photos().firstOrNull() }
        if (manualSubjectId != null && subjects.none { it.id == manualSubjectId }) manualSubjectId = null
    }
    LaunchedEffect(mode) {
        while (true) {
            if (mode == CameraMode.NOTE) autoTarget = currentArchiveTarget(scheduleViewModel)
            delay(30_000L)
        }
    }

    fun resetScan() {
        scanGeneration++
        gate.reset()
        result = null
        detection = null
        snapshot = null
        confirming = false
        candidates = emptyList()
        failure = null
    }

    fun switchMode(next: CameraMode) {
        if (next == mode || showNoteGalleryIntro) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        mode = next
        CameraModeStore.saveMode(next)
        resetScan()
        torch = false
    }

    fun takeNote() {
        if (showNoteGalleryIntro) return
        val capture = camera ?: return
        if (taking) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        taking = true
        val target = currentArchiveTarget(scheduleViewModel)
        val manualSubject = manualSubjectId
        capture.takePicture(ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching { saveCapturedPhoto(image, manualSubject, target, rotation) }
                        }
                        image.close()
                        taking = false
                        saved.onSuccess { photo ->
                            if (photo != null) {
                                lastPhoto = photo
                                subjects = withContext(Dispatchers.IO) { NoteStore.subjects() }
                            }
                        }.onFailure { failure = "这张没保存成功，再拍一次试试" }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    taking = false
                    failure = "拍失败了，再试一次"
                }
            })
    }

    // 扫码识别结果 → 路由
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(800)
            focusPoint = null
        }
    }

    LaunchedEffect(result, routeAttempt) {
        val raw = result ?: return@LaunchedEffect
        delay(260)
        confirming = false
        if (QrRouter.classify(raw) == QrRouter.Kind.Unknown) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            when (val outcome = QrRouter.route(context, raw)) {
                is QrRouter.Outcome.Handed -> {
                    outcome.note?.let {
                        runCatching { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
                    }
                    runCatching { onBack() }
                }
                is QrRouter.Outcome.TargetMissing -> failure = "未找到${outcome.targetApp}"
                is QrRouter.Outcome.Failed -> failure = "无法打开${outcome.targetApp}"
                QrRouter.Outcome.Unrecognized -> Unit
            }
        }
    }

    BackHandler {
        when {
            showNoteGalleryIntro -> Unit
            showGallery -> showGallery = false
            mode == CameraMode.NOTE && manualSubjectId != null -> manualSubjectId = null
            else -> onBack()
        }
    }

    // 符号纠正 + 连续累加：跨象限时走最短路径（0 ↔ 270 不会绕一圈）
    var continuousRotation by remember { mutableStateOf(0f) }
    LaunchedEffect(rotation) {
        val target = (((-rotation % 360) + 360) % 360).toFloat()
        val delta = ((target - continuousRotation + 540f) % 360f) - 180f
        continuousRotation += delta
    }
    CompositionLocalProvider(LocalCameraRotation provides continuousRotation) {
        Box(Modifier.fillMaxSize().background(Color.Black)
            .onGloballyPositioned { cameraBounds = it.boundsInWindow() }) {
            if (!granted.value) {
                PermissionEmpty(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onBack = onBack)
            } else {
                Column(Modifier.fillMaxSize().statusBarsPadding().then(
                    if (showNoteGalleryIntro) Modifier.clearAndSetSemantics {} else Modifier
                )) {
                    CameraTopBar(
                        mode = mode,
                        subjectLabel = subjectLabel(mode, manualSubjectId, subjects, autoTarget),
                        onBack = onBack,
                        onPickSubject = { showSubjectPicker = true }
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                        ) {
                            if (!showGallery) CameraPreviewHost(
                                mode = mode,
                                scanGeneration = scanGeneration,
                                manualZoomGeneration = manualZoomGeneration,
                                manualZoomRatio = zoomRatio,
                                onController = { controller ->
                                    camera = controller
                                    hasFlash = true
                                },
                                onZoom = { zoomState = it; zoomRatio = it.ratio },
                                onError = { cameraError = true },
                                keepScanning = false,
                                onScanFrame = { found, frozen ->
                                    if (mode == CameraMode.SCAN && result == null) {
                                        if (found.size == 1) {
                                            if (gate.accept(found.first().raw)) {
                                                snapshot = frozen
                                                result = found.first().raw
                                                detection = found.first()
                                                confirming = true
                                            } else frozen.recycle()
                                        } else {
                                            snapshot = frozen
                                            detection = null
                                            candidates = found
                                        }
                                    } else frozen.recycle()
                                }
                            )
                            snapshot?.takeUnless { it.isRecycled }?.let {
                                androidx.compose.foundation.Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            ScanOverlay(
                                detection = detection,
                                confirming = confirming,
                                scanning = mode == CameraMode.SCAN && result == null && snapshot == null,
                                dim = mode == CameraMode.SCAN
                            )
                            focusPoint?.let { point ->
                                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.9f),
                                        radius = 18.dp.toPx(),
                                        center = point,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx())
                                    )
                                }
                            }
                            if (candidates.size > 1 && result == null && mode == CameraMode.SCAN) {
                                ScannerTargetsOverlay(
                                    candidates = candidates,
                                    onSelect = { chosen ->
                                        candidates = emptyList()
                                        if (gate.accept(chosen.raw)) {
                                            result = chosen.raw
                                            detection = chosen
                                            confirming = true
                                        }
                                    }
                                )
                            }
                            ScannerZoomControls(
                                state = zoomState,
                                onZoomRatioChange = { ratio ->
                                    manualZoomGeneration++
                                    camera?.setZoomRatio(ratio)
                                    zoomRatio = ratio
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                            )
                        }
                        if (cameraError) {
                            Text("相机暂不可用", color = Color.White)
                        }
                    }
                    BottomControls(
                        mode = mode,
                        torch = torch,
                        hasFlash = hasFlash,
                        taking = taking,
                        thumbnailPath = lastPhoto?.let { NoteStore.photoFile(it).absolutePath },
                        onThumbnail = {
                            if (mode == CameraMode.SCAN) {
                                photoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                finishNoteGalleryIntro()
                                showGallery = true
                            }
                        },
                        onShutter = ::takeNote,
                        onTorch = {
                            torch = !torch
                            camera?.enableTorch(torch)
                        },
                        onMode = ::switchMode,
                        onThumbnailBounds = { galleryButtonBounds = it }
                    )
                }
            }

            result?.takeIf { !confirming }?.let { raw ->
                ScanResultCard(
                    raw = raw,
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("二维码", raw))
                    },
                    onContinue = ::resetScan,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp)
                )
            }

            failure?.let { message ->
                AppDialog(
                    title = message,
                    onDismiss = { failure = null },
                    actions = listOf(
                        AppDialogAction("知道了", onClick = { failure = null },
                            style = AppDialogActionStyle.Primary)
                    )
                )
            }

            if (showSubjectPicker) {
                SubjectPickerDialog(
                    subjects = subjects,
                    selectedId = manualSubjectId,
                    autoName = autoTarget?.let { archiveSubjectName(it.courseName) },
                    onPick = {
                        manualSubjectId = it
                        showSubjectPicker = false
                    },
                    onManage = {
                        showSubjectPicker = false
                        showSubjectManager = true
                    },
                    onDismiss = { showSubjectPicker = false }
                )
            }

            if (showSubjectManager) {
                NoteSubjectManagerDialog(
                    onDismiss = {
                        showSubjectManager = false
                        showSubjectPicker = true
                    },
                    onChanged = { noteRefreshTick++ }
                )
            }

            if (showGallery) {
                NoteGalleryScreen(
                    onDismiss = { showGallery = false },
                    onChanged = { noteRefreshTick++ }
                )
            }

            if (showNoteGalleryIntro) {
                NoteGalleryIntroOverlay(
                    galleryBounds = galleryButtonBounds.translate(-cameraBounds.topLeft),
                    onDismiss = ::finishNoteGalleryIntro,
                    onOpenGallery = {
                        finishNoteGalleryIntro()
                        showGallery = true
                    }
                )
            }
        }
    }
}

private fun subjectLabel(
    mode: CameraMode,
    manualSubjectId: String?,
    subjects: List<NoteSubject>,
    autoTarget: ArchiveTarget?
): String {
    if (mode == CameraMode.SCAN) return ""
    val manual = manualSubjectId?.let { id -> subjects.firstOrNull { it.id == id } }
    if (manual != null) return manual.name
    val auto = autoTarget?.courseName?.let(::archiveSubjectName)
    return if (auto != null) "自动 · $auto" else "未归档"
}

private fun currentArchiveTarget(scheduleViewModel: ScheduleViewModel): ArchiveTarget? {
    val config = scheduleViewModel.scheduleConfig.value ?: return null
    val courses = scheduleViewModel.schedule.value?.valueOrNull().orEmpty()
    val now = DebugClock.nowZonedDateTime()
    val holiday = scheduleViewModel.scheduleHolidays.value?.get(now.toLocalDate())
    return resolveArchiveTarget(
        nowMinutesOfDay = now.hour * 60 + now.minute,
        weekday = config.weekDay,
        week = config.week,
        isInSemester = config.isInSemester,
        holidayBlocksClasses = holiday?.isOffDay == true,
        slots = courses.mapNotNull(::archiveSlotOf)
    )
}

/** 拍照落盘：定科目 → 旋转像素 → 限长边 → JPEG → EXIF 归正 → 登记元数据。全部在 IO 线程。 */
private fun saveCapturedPhoto(
    image: ImageProxy,
    manualSubjectId: String?,
    target: ArchiveTarget?,
    elementRotation: Int
): NotePhoto? {
    val manual = manualSubjectId?.let { NoteStore.subject(it) }
    val subject = manual
        ?: target?.let { NoteStore.ensureSubject(archiveSubjectName(it.courseName)) }
        ?: NoteStore.unfiledSubject()
    val source = when {
        manual != null -> NoteSource.MANUAL
        target != null -> NoteSource.AUTO
        else -> NoteSource.UNFILED
    }
    val bitmap = image.toBitmap()
    // 实测（PKX110）：controller 出的是原始 sensor 像素（如 4096×3072 横向），
    // degrees=180 表示相对显示还需要翻转 180°，element 是物理朝向的 UI 补正。
    // 目标：竖持出竖图、横持出横图，且内容正立。
    val rawLandscape = bitmap.width > bitmap.height
    val devicePortrait = ((elementRotation % 180) + 180) % 180 == 0
    var base = if (rawLandscape && devicePortrait) 90 else 0
    if (((image.imageInfo.rotationDegrees % 360) + 360) % 360 == 180) base += 180
    val extra = ((base % 360) + 360) % 360
    val upright = if (extra != 0) {
        Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(extra.toFloat()) },
            true
        ).also { if (it !== bitmap) bitmap.recycle() }
    } else bitmap
    val scaled = upright.limitEdge(MAX_PHOTO_EDGE)
    if (scaled !== upright) upright.recycle()
    val file = File(NoteStore.subjectDir(subject.id), NoteStore.newPhotoFileName())
    return try {
        file.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, out) }
        scaled.recycle()
        runCatching {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                saveAttributes()
            }
        }
        NoteStore.addPhoto(file, subject.id, source, target?.courseName, target?.window)
    } catch (error: Exception) {
        file.delete()
        throw error
    } finally {
        if (!scaled.isRecycled) scaled.recycle()
    }
}

private fun Bitmap.limitEdge(maxEdge: Int): Bitmap {
    val edge = maxOf(width, height)
    if (edge <= maxEdge) return this
    val scale = maxEdge.toFloat() / edge
    return Bitmap.createScaledBitmap(
        this, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true
    )
}

@Composable
private fun CameraTopBar(
    mode: CameraMode,
    subjectLabel: String,
    onBack: () -> Unit,
    onPickSubject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Color.White,
                modifier = Modifier.size(18.dp).cameraRotated())
        }
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = mode == CameraMode.NOTE,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "subject-pill"
        ) { show ->
            if (show) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onPickSubject)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        subjectLabel,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(1.dp))
                    Icon(Icons.Rounded.ArrowDropDown, "切换科目", tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp))
                }
            } else Spacer(Modifier.width(1.dp))
        }
    }
}

@Composable
private fun ScanOverlay(
    detection: QrDetection?,
    confirming: Boolean,
    scanning: Boolean,
    dim: Boolean
) {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "scan-pulse")
        .animateFloat(
            initialValue = 0.5f,
            targetValue = 0.95f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1100),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "scan-pulse-alpha"
        )
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        // 目标框为正方形，居中，边长约为可用宽度的 3/4
        val side = minOf(size.width * 0.76f, size.height * 0.84f)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val corner = 28.dp.toPx()
        val stroke = 2.5.dp.toPx()
        if (dim) {
            val outside = androidx.compose.ui.graphics.Path.combine(
                androidx.compose.ui.graphics.PathOperation.Difference,
                androidx.compose.ui.graphics.Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                },
                androidx.compose.ui.graphics.Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(left, top, left + side, top + side))
                }
            )
            drawPath(outside, Color.Black.copy(alpha = 0.45f))
        }
        if (scanning) {
            val color = Color.White.copy(alpha = pulse)
            listOf(
                Offset(left, top), Offset(left + side, top),
                Offset(left, top + side), Offset(left + side, top + side)
            ).forEachIndexed { index, point ->
                val dx = if (index % 2 == 0) 1 else -1
                val dy = if (index < 2) 1 else -1
                drawLine(color, point, Offset(point.x + dx * corner, point.y), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(color, point, Offset(point.x, point.y + dy * corner), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        val outline = detection?.takeIf { it.corners.size == 4 }
        if (outline != null) {
            // 检测角点归一化于整幅预览（与候选码浮层同一口径），不能塞进方框
            val path = androidx.compose.ui.graphics.Path()
            outline.corners.forEachIndexed { i, p ->
                val x = p.x * size.width
                val y = p.y * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Color.White.copy(alpha = if (confirming) 1f else 0.7f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        }
    }
}

@Composable
private fun BottomControls(
    mode: CameraMode,
    torch: Boolean,
    hasFlash: Boolean,
    taking: Boolean,
    thumbnailPath: String?,
    onThumbnail: () -> Unit,
    onShutter: () -> Unit,
    onTorch: () -> Unit,
    onMode: (CameraMode) -> Unit,
    onThumbnailBounds: (Rect) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier.size(CONTROL_SLOT.dp).clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .onGloballyPositioned { onThumbnailBounds(it.boundsInWindow()) }
                    .semantics { contentDescription = if (mode == CameraMode.NOTE) "笔记入口" else "相册" }
                    .clickable(onClick = onThumbnail),
                contentAlignment = Alignment.Center
            ) {
                if (mode == CameraMode.NOTE) {
                    NoteThumbnail(
                        path = thumbnailPath,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp))
                    )
                } else {
                    Icon(Icons.Rounded.PhotoLibrary, "相册", tint = Color.White,
                        modifier = Modifier.size(20.dp).cameraRotated())
                }
            }
            AnimatedContent(
                targetState = mode == CameraMode.NOTE,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "shutter"
            ) { show ->
                if (show) {
                    Box(
                        Modifier
                            .size(SHUTTER_SIZE.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (taking) 0.4f else 1f))
                            .border(2.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable(enabled = !taking, role = Role.Button, onClick = onShutter)
                    )
                } else Spacer(Modifier.size(SHUTTER_SIZE.dp))
            }
            Box(
                Modifier.size(CONTROL_SLOT.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(enabled = hasFlash, onClick = onTorch),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    "闪光灯",
                    tint = if (hasFlash) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp).cameraRotated()
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeSelector(mode = mode, onMode = onMode)
        }
    }
}

/** 模式选择器：选中项恒定居中并带胶囊高亮，左右拖动或点击切换。 */
@Composable
private fun ModeSelector(mode: CameraMode, onMode: (CameraMode) -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // 两个等宽标签；行位移半个「项宽 + 间距」，就能让选中项中心落回屏幕中线
    val stepPx = with(density) { ((MODE_ITEM_WIDTH + MODE_ITEM_GAP) / 2).toPx() }
    val index = if (mode == CameraMode.SCAN) 0 else 1
    val basePx = if (index == 0) stepPx else -stepPx
    val offset = remember { Animatable(basePx) }
    LaunchedEffect(basePx) {
        offset.animateTo(basePx, tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val dragState = rememberDraggableState { delta ->
        val limit = stepPx
        val next = (offset.value + delta).coerceIn(-limit, limit)
        scope.launch { offset.snapTo(next) }
    }
    Box(Modifier.fillMaxWidth().height(MODE_ITEM_HEIGHT), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val shift = offset.value - basePx
                        val threshold = with(density) { 14.dp.toPx() }
                        val toNote = shift < -threshold || velocity < -900f
                        val toScan = shift > threshold || velocity > 900f
                        when {
                            toNote && index == 0 -> onMode(CameraMode.NOTE)
                            toScan && index == 1 -> onMode(CameraMode.SCAN)
                            else -> scope.launch {
                                offset.animateTo(basePx, tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                            }
                        }
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(MODE_ITEM_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CameraMode.entries.forEach { entry ->
                val selected = entry == mode
                Box(
                    modifier = Modifier
                        .width(MODE_ITEM_WIDTH)
                        .height(MODE_ITEM_HEIGHT)
                        .clip(CircleShape)
                        .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable(role = Role.Button, onClick = { onMode(entry) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (entry == CameraMode.SCAN) "扫码" else "笔记",
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        letterSpacing = 0.4.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanResultCard(
    raw: String,
    onCopy: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (QrRouter.classify(raw) == QrRouter.Kind.Unknown) "不支持此二维码" else "扫描结果",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            if (raw.length <= 180) raw else raw.take(180) + "…",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallAction("复制", onCopy, Modifier.weight(1f))
            SmallAction("继续扫描", onContinue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PermissionEmpty(onRequest: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("需要相机权限才能扫码或拍笔记", color = Color.White)
        Spacer(Modifier.height(16.dp))
        SmallAction("授予权限", onRequest, Modifier.fillMaxWidth(0.6f))
        Spacer(Modifier.height(8.dp))
        SmallAction("返回", onBack, Modifier.fillMaxWidth(0.6f))
    }
}

@Composable
private fun SubjectPickerDialog(
    subjects: List<NoteSubject>,
    selectedId: String?,
    autoName: String?,
    onPick: (String?) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = "归档到",
        onDismiss = onDismiss,
        contentScrollable = true,
        actions = listOf(
            AppDialogAction("管理科目", onClick = onManage, style = AppDialogActionStyle.Primary),
            AppDialogAction("取消", onClick = onDismiss)
        ),
        content = {
            Column {
                PickerRow(
                    label = autoName?.let { "自动 · $it" } ?: "自动归档（当前无课）",
                    selected = selectedId == null,
                    onClick = { onPick(null) }
                )
                subjects.filter { it.id != UNFILED_SUBJECT_ID }.forEach { subject ->
                    PickerRow(
                        label = subject.name,
                        selected = selectedId == subject.id,
                        onClick = { onPick(subject.id) }
                    )
                }
                PickerRow(
                    label = "未归档",
                    selected = selectedId == UNFILED_SUBJECT_ID,
                    onClick = { onPick(UNFILED_SUBJECT_ID) }
                )
            }
        }
    )
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
    }
}
