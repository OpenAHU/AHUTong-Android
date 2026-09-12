package com.ahu.ahutong.ui.screen.main

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.calendar.SchoolCalendarYearPolicy
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.mock.MockScenarioController
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCard
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppHeaderIconButton
import com.ahu.ahutong.ui.components.AppPageLayout
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.utils.FileUtils
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SchoolCalendar(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val yearListState = rememberLazyListState()

    var calendarYears by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedYear by remember { mutableStateOf<String?>(null) }
    var calendarFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeRequestKey by remember { mutableStateOf<String?>(null) }
    val mockRefreshRevision by MockScenarioController.refreshRevisions().collectAsState()

    val fetchImage: (String?, Boolean) -> Unit = { year, forceRefresh ->
        val requestKey = year ?: LEGACY_CALENDAR_KEY
        val fileName = year?.let(SchoolCalendarYearPolicy::cacheFileName) ?: LEGACY_CALENDAR_FILE
        val cached = FileUtils.getImageFile(context, fileName)
        val keepCurrentImage = FileUtils.isValidImage(cached) &&
            calendarFile?.absolutePath == cached.absolutePath
        activeRequestKey = requestKey

        if (!forceRefresh && FileUtils.isValidImage(cached)) {
            calendarFile = cached
            isLoading = false
            errorMessage = null
        } else {
            scope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    isLoading = true
                    progress = 0f
                    errorMessage = null
                    if (!keepCurrentImage) calendarFile = null
                }

                val file = runCatching {
                    val result = if (year == null) {
                        AHURepository.getSchoolCalendar()
                    } else {
                        AHURepository.getSchoolCalendar(year)
                    }
                    val response = result.data
                    if (!result.isSuccessful || response?.isSuccessful != true) return@runCatching null
                    val body = response.body() ?: return@runCatching null
                    FileUtils.saveResponseBodyToFileAtomically(context, body, fileName) {
                        progress = it
                    }
                }.getOrNull()

                withContext(Dispatchers.Main) {
                    if (activeRequestKey == requestKey) {
                        isLoading = false
                        if (file != null && FileUtils.isValidImage(file)) {
                            calendarFile = file
                            errorMessage = null
                        } else if (FileUtils.isValidImage(cached)) {
                            calendarFile = cached
                            errorMessage = "刷新失败，已显示本地缓存"
                        } else {
                            calendarFile = null
                            errorMessage = "获取校历失败，请检查网络后重试"
                        }
                    }
                }
            }
        }
    }

    val fetchCatalog: (Boolean) -> Unit = { forceRefresh ->
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isLoading = true
                progress = 0f
                errorMessage = null
            }

            val response = runCatching { AHURepository.getSchoolCalendarYears() }.getOrNull()
            val catalog = response?.data
            val years = if (response?.isSuccessful == true && catalog != null) {
                SchoolCalendarYearPolicy.normalize(catalog.years)
            } else {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                calendarYears = years
                if (years.isNotEmpty()) {
                    val targetYear = SchoolCalendarYearPolicy.select(
                        years = years,
                        latestYear = catalog?.latestYear,
                        currentSelection = selectedYear
                    )
                    selectedYear = targetYear
                    if (targetYear != null) fetchImage(targetYear, forceRefresh)
                } else {
                    selectedYear = null
                    fetchImage(null, forceRefresh)
                }
            }
        }
    }

    fun saveCalendar(file: File) {
        scope.launch(Dispatchers.IO) {
            val success = runCatching { FileUtils.saveImageToGallery(context, file) }.isSuccess
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (success) "校历已保存到相册" else "保存失败，请稍后重试",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val savePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val file = calendarFile
        if (isGranted && file != null) {
            saveCalendar(file)
        } else {
            Toast.makeText(context, "需要存储权限才能保存校历", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestSave() {
        val file = calendarFile
        if (file == null) {
            Toast.makeText(context, "校历还没有加载完成", Toast.LENGTH_SHORT).show()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            savePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveCalendar(file)
        }
    }

    fun selectYear(year: String) {
        if (year != selectedYear) {
            selectedYear = year
            fetchImage(year, false)
        }
    }

    LaunchedEffect(Unit) {
        val legacyCache = FileUtils.getImageFile(context, LEGACY_CALENDAR_FILE)
        if (!AHUCache.getMockData() && FileUtils.isValidImage(legacyCache)) {
            calendarFile = legacyCache
        }
        fetchCatalog(false)
    }

    LaunchedEffect(mockRefreshRevision) {
        if (mockRefreshRevision > 0 && AHUCache.getMockData()) {
            fetchCatalog(true)
        }
    }

    LaunchedEffect(selectedYear, calendarYears) {
        val index = calendarYears.indexOf(selectedYear)
        if (index >= 0) yearListState.animateScrollToItem(index)
    }

    var imageScale by remember(calendarFile) { mutableFloatStateOf(1f) }
    var imageOffset by remember(calendarFile) { mutableStateOf(Offset.Zero) }
    val selectedIndex = calendarYears.indexOf(selectedYear)
    val newerYear = calendarYears.getOrNull(selectedIndex - 1)
    val olderYear = calendarYears.getOrNull(selectedIndex + 1)
    val displayYear = selectedYear?.let(SchoolCalendarYearPolicy::displayName) ?: "当前校历"

    fun resetZoom() {
        imageScale = 1f
        imageOffset = Offset.Zero
    }

    AppPageLayout(
        title = "校历",
        onBack = { navController.popBackStack() },
        modifier = Modifier
            .fillMaxSize()
            .appLiquidGlassSceneBackground(96.n1 withNight 10.n1),
        actions = {
            AppHeaderIconButton(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "刷新校历",
                onClick = { fetchCatalog(true) }
            )
            AppHeaderIconButton(
                imageVector = Icons.Rounded.Download,
                contentDescription = "保存校历",
                onClick = ::requestSave
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CalendarOverviewCard(
                displayYear = displayYear,
                availableCount = calendarYears.size,
                usingLegacyCalendar = selectedYear == null
            )

            if (calendarYears.isNotEmpty()) {
                LazyRow(
                    state = yearListState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(calendarYears, key = { it }) { year ->
                        val selected = year == selectedYear
                        AppFilterChip(
                            selected = selected,
                            onClick = { selectYear(year) },
                            label = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(year.replace("-", "—"))
                                }
                            }
                        )
                    }
                }
            }

            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    calendarFile?.let { file ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(file)
                                .crossfade(true)
                                .build(),
                            contentDescription = displayYear,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(file) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val nextScale = (imageScale * zoom).coerceIn(1f, 5f)
                                        imageScale = nextScale
                                        imageOffset = if (nextScale == 1f) Offset.Zero else imageOffset + pan
                                    }
                                }
                                .pointerInput(file) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (imageScale > 1f) {
                                                resetZoom()
                                            } else {
                                                imageScale = 2f
                                            }
                                        }
                                    )
                                }
                                .graphicsLayer(
                                    scaleX = imageScale,
                                    scaleY = imageScale,
                                    translationX = imageOffset.x,
                                    translationY = imageOffset.y
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (isLoading && calendarFile != null) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                        )
                    }

                    when {
                        isLoading && calendarFile == null -> CalendarLoadingState(progress)
                        calendarFile == null -> CalendarEmptyState(
                            message = errorMessage ?: "暂无可用校历",
                            onRetry = { fetchCatalog(true) }
                        )
                    }

                    if (calendarFile != null) {
                        ZoomControls(
                            scale = imageScale,
                            onZoomOut = {
                                imageScale = (imageScale - 0.5f).coerceAtLeast(1f)
                                if (imageScale == 1f) imageOffset = Offset.Zero
                            },
                            onReset = ::resetZoom,
                            onZoomIn = { imageScale = (imageScale + 0.5f).coerceAtMost(5f) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                        )
                    }

                    if (errorMessage != null && calendarFile != null && !isLoading) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = errorMessage!!,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            CalendarYearNavigation(
                newerYear = newerYear,
                olderYear = olderYear,
                onSelectYear = ::selectYear
            )
        }
    }
}

@Composable
private fun CalendarOverviewCard(
    displayYear: String,
    availableCount: Int,
    usingLegacyCalendar: Boolean
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayYear,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        availableCount > 0 -> "$availableCount 个学年可选 · 双击复位，双指缩放"
                        usingLegacyCalendar -> "兼容模式 · 双击复位，双指缩放"
                        else -> "正在读取校历列表"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarLoadingState(progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppCircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (progress > 0f) {
                "正在下载 ${(progress * 100).roundToInt()}%"
            } else {
                "正在获取校历"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalendarEmptyState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        AppButton(onClick = onRetry, variant = AppButtonVariant.Secondary) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("重新加载")
        }
    }
}

@Composable
private fun ZoomControls(
    scale: Float,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onZoomOut, enabled = scale > 1f) {
                Icon(Icons.Rounded.ZoomOut, contentDescription = "缩小")
            }
            Surface(
                onClick = onReset,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${(scale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            IconButton(onClick = onZoomIn, enabled = scale < 5f) {
                Icon(Icons.Rounded.ZoomIn, contentDescription = "放大")
            }
        }
    }
}

@Composable
private fun CalendarYearNavigation(
    newerYear: String?,
    olderYear: String?,
    onSelectYear: (String) -> Unit
) {
    if (newerYear == null && olderYear == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppButton(
            onClick = { olderYear?.let(onSelectYear) },
            modifier = Modifier.weight(1f),
            enabled = olderYear != null,
            variant = AppButtonVariant.Secondary
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(olderYear?.let { "较早 ${it.substringBefore('-')}" } ?: "已是最早")
        }
        AppButton(
            onClick = { newerYear?.let(onSelectYear) },
            modifier = Modifier.weight(1f),
            enabled = newerYear != null,
            variant = AppButtonVariant.Secondary
        ) {
            Text(newerYear?.let { "较新 ${it.substringBefore('-')}" } ?: "已是最新")
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private const val LEGACY_CALENDAR_FILE = "xiaoli.jpg"
private const val LEGACY_CALENDAR_KEY = "legacy"
