package com.ahu.ahutong.ui.screen.camera

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import com.ahu.ahutong.ui.haptics.hapticCombinedClickable as combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.data.notes.NotePhoto
import com.ahu.ahutong.data.notes.NoteStore
import com.ahu.ahutong.data.notes.NoteSubject
import com.ahu.ahutong.data.notes.UNFILED_SUBJECT_ID
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppPageScaffold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 笔记相册（标准二级页）：日期分组 + 3 列网格 + 下拉筛选；长按多选后可移动/导出/删除。
 *
 * 页面骨架、背景与标题栏全部复用 [AppPageScaffold]，不另造黑底页面。
 */
@Composable
fun NoteGalleryScreen(
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var subjects by remember { mutableStateOf<List<NoteSubject>>(emptyList()) }
    var filterId by remember { mutableStateOf<String?>(null) }
    var photos by remember { mutableStateOf<List<NotePhoto>>(emptyList()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(filterId, refreshTick) {
        val (loadedSubjects, loadedPhotos) = withContext(Dispatchers.IO) {
            NoteStore.subjects() to NoteStore.photos(filterId)
        }
        subjects = loadedSubjects
        photos = loadedPhotos
    }

    fun toast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun refreshAll() {
        selection = emptySet()
        refreshTick++
        onChanged()
    }

    val filterLabel = when (filterId) {
        null -> "全部"
        UNFILED_SUBJECT_ID -> "未归档"
        else -> subjects.firstOrNull { it.id == filterId }?.name ?: "全部"
    }
    val multiSelect = selection.isNotEmpty()

    BackHandler {
        when {
            viewerIndex != null -> viewerIndex = null
            multiSelect -> selection = emptySet()
            else -> onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AppPageScaffold(
            title = if (multiSelect) "已选 ${selection.size} 张" else "笔记",
            onBack = { if (multiSelect) selection = emptySet() else onDismiss() },
            scrollEnabled = false,
            bottomPadding = if (multiSelect) 96.dp else 24.dp,
            trailingContent = {
                if (multiSelect) {
                    Text(
                        if (selection.size == photos.size && photos.isNotEmpty()) "取消全选" else "全选",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                selection = if (selection.size == photos.size) {
                                    emptySet()
                                } else photos.map { it.id }.toSet()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                } else {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { showFilterMenu = true }
                                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                filterLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                Icons.Rounded.ArrowDropDown, "筛选科目",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(text = { Text("全部") }, onClick = {
                                filterId = null
                                showFilterMenu = false
                            })
                            DropdownMenuItem(text = { Text("未归档") }, onClick = {
                                filterId = UNFILED_SUBJECT_ID
                                showFilterMenu = false
                            })
                            subjects.filter { it.id != UNFILED_SUBJECT_ID }.forEach { subject ->
                                DropdownMenuItem(text = { Text(subject.name) }, onClick = {
                                    filterId = subject.id
                                    showFilterMenu = false
                                })
                            }
                        }
                    }
                }
            },
            freeContent = {
                if (photos.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("还没有笔记", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val grouped = remember(photos) {
                        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                        val today = fmt.format(Date())
                        photos.groupBy {
                            val day = fmt.format(Date(it.takenAt))
                            if (day == today) "今天" else day
                        }.toList()
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = 4.dp, bottom = if (multiSelect) 96.dp else 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        grouped.forEach { (label, dayPhotos) ->
                            item(key = "header_$label", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                )
                            }
                            items(count = dayPhotos.size, key = { dayPhotos[it].id }) { index ->
                                val photo = dayPhotos[index]
                                val overallIndex = photos.indexOf(photo)
                                GalleryCell(
                                    photo = photo,
                                    selectedIndex = selection.toList().indexOf(photo.id).takeIf { it >= 0 },
                                    onClick = {
                                        if (selection.isNotEmpty()) {
                                            selection = if (photo.id in selection) selection - photo.id
                                            else selection + photo.id
                                        } else viewerIndex = overallIndex
                                    },
                                    onLongClick = {
                                        selection = if (photo.id in selection) selection - photo.id
                                        else selection + photo.id
                                    }
                                )
                            }
                        }
                    }
                }

                if (multiSelect) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .navigationBarsPadding()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GalleryAction("移动") { showMoveDialog = true }
                        GalleryAction("导出") {
                            if (busy) return@GalleryAction
                            busy = true
                            scope.launch {
                                val count = withContext(Dispatchers.IO) {
                                    NoteStore.exportToGallery(selection)
                                }
                                busy = false
                                toast("已导出 $count 张")
                                selection = emptySet()
                            }
                        }
                        GalleryAction("删除", danger = true) { showDeleteConfirm = true }
                    }
                }
            }
        )

        if (showMoveDialog) {
            val targets = subjects
            AppDialog(
                title = "移动到",
                onDismiss = { showMoveDialog = false },
                actions = listOf(AppDialogAction("取消", onClick = { showMoveDialog = false })),
                content = {
                    Column {
                        targets.forEach { subject ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!busy) {
                                            busy = true
                                            val ids = selection
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    NoteStore.movePhotos(ids, subject.id)
                                                }
                                                busy = false
                                                showMoveDialog = false
                                                refreshAll()
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(subject.name, style = MaterialTheme.typography.bodyLarge)
                                if (subject.id == filterId) {
                                    Text(
                                        "当前",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }

        if (showDeleteConfirm) {
            AppDialog(
                title = "删除这 ${selection.size} 张？",
                onDismiss = { showDeleteConfirm = false },
                actions = listOf(
                    AppDialogAction("取消", onClick = { showDeleteConfirm = false }),
                    AppDialogAction("删除", onClick = {
                        val ids = selection
                        showDeleteConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) { NoteStore.deletePhotos(ids) }
                            refreshAll()
                        }
                    }, style = AppDialogActionStyle.Primary)
                )
            )
        }

        viewerIndex?.let { start ->
            NotePagerViewer(
                photos = photos,
                startIndex = start,
                onClose = { viewerIndex = null }
            )
        }
    }
}

@Composable
private fun GalleryCell(
    photo: NotePhoto,
    selectedIndex: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        NoteThumbnail(
            path = NoteStore.photoFile(photo).absolutePath,
            modifier = Modifier.fillMaxSize()
        )
        if (selectedIndex != null) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${selectedIndex + 1}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GalleryAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp)
    )
}

/** 全屏查看：左右滑翻页，单页双指缩放（图片查看器保持黑底）。 */
@Composable
private fun NotePagerViewer(
    photos: List<NotePhoto>,
    startIndex: Int,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, photos.lastIndex.coerceAtLeast(0))
    ) { photos.size }
    // 放大后禁用翻页，避免"想拖图却翻到下一张"
    var zoomed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed
        ) { page ->
            ZoomablePhoto(
                path = NoteStore.photoFile(photos[page]).absolutePath,
                pager = pagerState,
                onZoomChanged = { zoomed = it }
            )
        }
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, "关闭", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 图片查看手势：
 * - 1× 时单指横向拖动不消费 → 交给 pager 翻页；
 * - 双指缩放始终可用；放大后单指拖动改为平移，并限制在图片边界内；
 * - 贴边继续拖先阻尼（最多 30% 屏宽），松手超过 12% 才翻页，否则回弹。
 */
@Composable
private fun ZoomablePhoto(
    path: String,
    pager: androidx.compose.foundation.pager.PagerState,
    onZoomChanged: (Boolean) -> Unit
) {
    var container by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var overflowX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    fun fitScale(): Float {
        if (imageSize.width == 0 || container.width == 0) return 0f
        return min(container.width.toFloat() / imageSize.width, container.height.toFloat() / imageSize.height)
    }

    fun limitX(s: Float): Float =
        if (imageSize.width == 0 || container.width == 0) 0f
        else max(0f, (imageSize.width * fitScale() * s - container.width) / 2f)

    fun limitY(s: Float): Float =
        if (imageSize.height == 0 || container.height == 0) 0f
        else max(0f, (imageSize.height * fitScale() * s - container.height) / 2f)

    suspend fun animateTo(from: Float, to: Float, apply: (Float) -> Unit) {
        androidx.compose.animation.core.animate(
            initialValue = from,
            targetValue = to,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f)
        ) { value, _ -> apply(value) }
    }

    suspend fun settle() {
        val threshold = container.width * 0.12f
        if (scale > 1.01f && kotlin.math.abs(overflowX) > threshold) {
            val target = if (overflowX < 0f) pager.currentPage + 1 else pager.currentPage - 1
            val next = target.coerceIn(0, pager.pageCount - 1)
            overflowX = 0f
            animateTo(scale, 1f) { scale = it }
            offsetX = 0f
            offsetY = 0f
            onZoomChanged(false)
            if (next != pager.currentPage) pager.animateScrollToPage(next)
            return
        }
        if (overflowX != 0f) animateTo(overflowX, 0f) { overflowX = it }
        if (scale < 1f) animateTo(scale, 1f) { scale = it }
        val lx = limitX(scale)
        val ly = limitY(scale)
        val cx = offsetX.coerceIn(-lx, lx)
        val cy = offsetY.coerceIn(-ly, ly)
        if (cx != offsetX) animateTo(offsetX, cx) { offsetX = it }
        if (cy != offsetY) animateTo(offsetY, cy) { offsetY = it }
        onZoomChanged(scale > 1.01f)
    }

    val image by produceState<ImageBitmap?>(null, path) {
        value = NoteThumbnails.load(path, maxEdge = 1600)
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { container = it },
        contentAlignment = Alignment.Center
    ) {
        image?.let { bitmap ->
            if (imageSize == IntSize.Zero) imageSize = IntSize(bitmap.width, bitmap.height)
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX + overflowX
                        translationY = offsetY
                    }
                    .pointerInput(path, container, imageSize) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2) {
                                    val zoom = event.calculateZoom()
                                    // 手势层挂在已缩放的节点里：local 位移 = 屏幕位移 / scale，
                                    // 平移量必须乘回去，否则越大越不跟手。
                                    val pan = event.calculatePan() * scale
                                    scale = (scale * zoom).coerceIn(0.75f, 6f)
                                    if (scale > 1f) {
                                        val lx = limitX(scale)
                                        val ly = limitY(scale)
                                        val nx = offsetX + pan.x
                                        if (nx > lx || nx < -lx) {
                                            offsetX = nx.coerceIn(-lx, lx)
                                            val excess = nx - offsetX
                                            val cap = container.width * 0.3f
                                            overflowX = (overflowX + excess * 0.35f).coerceIn(-cap, cap)
                                        } else {
                                            offsetX = nx
                                            overflowX = 0f
                                        }
                                        offsetY = (offsetY + pan.y).coerceIn(-ly, ly)
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                        overflowX = 0f
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (scale > 1.01f) {
                                    val pan = event.calculatePan() * scale
                                    val lx = limitX(scale)
                                    val ly = limitY(scale)
                                    val nx = offsetX + pan.x
                                    if (nx > lx || nx < -lx) {
                                        offsetX = nx.coerceIn(-lx, lx)
                                        val excess = nx - offsetX
                                        val cap = container.width * 0.3f
                                        overflowX = (overflowX + excess * 0.35f).coerceIn(-cap, cap)
                                    } else {
                                        offsetX = nx
                                        overflowX = 0f
                                    }
                                    offsetY = (offsetY + pan.y).coerceIn(-ly, ly)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            onZoomChanged(scale > 1.01f)
                            scope.launch { settle() }
                        }
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}
